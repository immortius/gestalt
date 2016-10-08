/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.entitysystem.transaction.inmemory;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.ResourceUrn;
import org.terasology.entitysystem.component.ComponentManager;
import org.terasology.entitysystem.component.ComponentType;
import org.terasology.entitysystem.component.PropertyAccessor;
import org.terasology.entitysystem.core.Component;
import org.terasology.entitysystem.core.EntityManager;
import org.terasology.entitysystem.core.EntityRef;
import org.terasology.entitysystem.prefab.GeneratedFromEntityRecipeComponent;
import org.terasology.entitysystem.transaction.EntityTransaction;
import org.terasology.entitysystem.transaction.TransactionEventListener;
import org.terasology.entitysystem.transaction.exception.ComponentAlreadyExistsException;
import org.terasology.entitysystem.transaction.exception.ComponentDoesNotExistException;
import org.terasology.entitysystem.transaction.references.CoreEntityRef;
import org.terasology.entitysystem.transaction.references.NewEntityRef;
import org.terasology.entitysystem.transaction.references.NullEntityRef;
import org.terasology.entitysystem.prefab.EntityRecipe;
import org.terasology.entitysystem.prefab.Prefab;
import org.terasology.entitysystem.prefab.PrefabRef;
import org.terasology.naming.Name;
import org.terasology.util.collection.TypeKeyedMap;

import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Transaction handling for a single thread.
 */
public class InMemoryTransaction implements EntityTransaction {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTransaction.class);

    private final EntityManager entityManager;
    private final EntityStore entityStore;
    private final ComponentManager componentManager;
    private final List<TransactionEventListener> eventListeners;

    private final Deque<TransactionState> transactionState = Queues.newArrayDeque();

    InMemoryTransaction(EntityManager entityManager, EntityStore entityStore, ComponentManager componentManager, List<TransactionEventListener> eventListeners) {
        this.entityManager = entityManager;
        this.entityStore = entityStore;
        this.componentManager = componentManager;
        this.eventListeners = eventListeners;
    }

    public void begin() {
        transactionState.push(new TransactionState(entityStore));
        eventListeners.forEach(TransactionEventListener::onBegin);
    }

    @SuppressWarnings("unchecked")
    public void commit() throws ConcurrentModificationException {
        Preconditions.checkState(isActive(), "No active transaction to commit");
        TransactionState state = transactionState.pop();
        try (ClosableLock ignored = entityStore.lock(state.expectedEntityRevisions.keySet())) {
            // Check entity revisions
            for (long entityId : state.expectedEntityRevisions.keySet()) {
                if (state.expectedEntityRevisions.get(entityId) != entityStore.getEntityRevision(entityId)) {
                    for (NewEntityRef ref : state.createdEntities) {
                        ref.setInnerEntityRef(NullEntityRef.get());
                    }
                    eventListeners.forEach(TransactionEventListener::onRollback);
                    throw new ConcurrentModificationException("Entity " + entityId + " modified outside of transaction");
                }
            }

            for (NewEntityRef newEntity : state.createdEntities) {
                Set<Class<? extends Component>> componentTypes = newEntity.getComponentTypes();
                if (!componentTypes.isEmpty()) {
                    long entityId = entityStore.createEntityId();
                    newEntity.setInnerEntityRef(new CoreEntityRef(entityManager, entityId));
                } else {
                    newEntity.setInnerEntityRef(NullEntityRef.get());
                }
            }

            for (NewEntityRef newEntity : state.createdEntities) {
                Set<Class<? extends Component>> componentTypes = newEntity.getComponentTypes();
                if (!componentTypes.isEmpty()) {
                    long entityId = newEntity.getInnerEntityRef().get().getId();
                    ClosableLock lock = entityStore.lock(Sets.newHashSet(entityId));
                    for (Class componentType : componentTypes) {
                        Component comp = (Component) newEntity.getComponent(componentType).get();
                        cleanUpEntityRefs(componentType, comp);
                        entityStore.add(entityId, componentType, comp);
                    }
                    lock.close();
                }
                newEntity.activateInnerRef();
            }

            // Apply changes
            for (CacheEntry comp : state.entityCache.values()) {
                switch (comp.getAction()) {
                    case ADD:
                        cleanUpEntityRefs(comp.componentType, comp.component);
                        if (!entityStore.add(comp.getEntityId(), comp.getComponentType(), comp.getComponent())) {
                            throw new RuntimeException("Entity state does not match expected.");
                        }
                        break;
                    case REMOVE:
                        if (entityStore.remove(comp.getEntityId(), comp.getComponentType()) == null) {
                            throw new RuntimeException("Entity state does not match expected.");
                        }
                        break;
                    case UPDATE:
                        cleanUpEntityRefs(comp.componentType, comp.component);
                        if (!entityStore.update(comp.getEntityId(), comp.getComponentType(), comp.getComponent())) {
                            throw new RuntimeException("Entity state does not match expected.");
                        }
                        break;
                }
            }

            eventListeners.forEach(TransactionEventListener::onCommit);
        }
    }

    private void cleanUpEntityRefs(Class<? extends Component> componentType, Component component) {
        ComponentType<?> type = componentManager.getType(componentType);
        for (PropertyAccessor property : type.getPropertyInfo().getPropertiesOfType(EntityRef.class)) {
            Object o = property.get(component);
            if (o instanceof NewEntityRef) {
                NewEntityRef entityRef = (NewEntityRef) o;
                entityRef.getInnerEntityRef().ifPresent((x) -> property.set(component, x));
            }
        }
    }

    public void rollback() {
        Preconditions.checkState(isActive(), "No active transaction to rollback");

        TransactionState oldState = transactionState.pop();
        for (NewEntityRef ref : oldState.createdEntities) {
            ref.setInnerEntityRef(NullEntityRef.get());
        }
        eventListeners.forEach(TransactionEventListener::onRollback);
    }

    private TransactionState getState() {
        Preconditions.checkState(!transactionState.isEmpty(), "No active transaction");
        return transactionState.peek();
    }

    public EntityRef createEntity() {
        TransactionState state = getState();
        NewEntityRef newEntityRef = new NewEntityRef(componentManager);
        state.createdEntities.add(newEntityRef);
        return newEntityRef;
    }

    @Override
    public boolean exists(long id) {
        TransactionState state = getState();
        state.cacheEntity(id);
        return state.expectedEntityRevisions.get(id) != 0;
    }

    @Override
    public <T extends Component> Optional<T> getComponent(long entityId, Class<T> componentType) {
        TransactionState state = getState();
        CacheEntry<T> cacheEntry = state.getCacheEntry(entityId, componentType);
        return Optional.ofNullable(cacheEntry.getComponent());
    }

    @Override
    public Set<Class<? extends Component>> getEntityComposition(long entityId) {
        TransactionState state = getState();
        Set<Class<? extends Component>> result = Sets.newHashSet();
        state.cacheEntity(entityId);
        for (Map.Entry<Class<? extends Component>, CacheEntry> entry : state.entityCache.row(entityId).entrySet()) {
            if (entry.getValue().getComponent() != null) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public TypeKeyedMap<Component> getEntityComponents(long entityId) {
        TransactionState state = getState();
        TypeKeyedMap<Component> result = new TypeKeyedMap<>();
        state.cacheEntity(entityId);
        for (Map.Entry<Class<? extends Component>, CacheEntry> entry : state.entityCache.row(entityId).entrySet()) {
            if (entry.getValue().getComponent() != null) {
                result.put(entry.getValue().getComponentType(), entry.getValue().getComponent());
            }
        }
        return result;
    }

    @Override
    public <T extends Component> T addComponent(long entityId, Class<T> componentType) {
        TransactionState state = getState();
        CacheEntry<T> cacheEntry = state.getCacheEntry(entityId, componentType);
        if (cacheEntry.getComponent() != null) {
            throw new ComponentAlreadyExistsException("Entity " + entityId + " already has a component of type " + componentType.getSimpleName());
        }
        T newComp = componentManager.create(componentType);
        cacheEntry.setComponent(newComp);
        switch (cacheEntry.getAction()) {
            case REMOVE:
                cacheEntry.setAction(Action.UPDATE);
                break;
            default:
                cacheEntry.setAction(Action.ADD);
                break;
        }
        return newComp;
    }

    @Override
    public <T extends Component> void removeComponent(long entityId, Class<T> componentType) {
        TransactionState state = getState();
        CacheEntry<T> cacheEntry = state.getCacheEntry(entityId, componentType);
        if (cacheEntry.getComponent() == null) {
            throw new ComponentDoesNotExistException("Entity " + entityId + " does not have a component of type " + componentType.getSimpleName());
        }
        cacheEntry.setComponent(null);
        switch (cacheEntry.getAction()) {
            case ADD:
                cacheEntry.setAction(Action.NONE);
                break;
            default:
                cacheEntry.setAction(Action.REMOVE);
                break;
        }
    }

    @Override
    public EntityRef createEntity(Prefab prefab) {
        Map<Name, EntityRef> entities = createEntities(prefab);
        return entities.get(prefab.getRootEntityUrn().getFragmentName());
    }

    @Override
    public Map<Name, EntityRef> createEntities(Prefab prefab) {
        Map<Name, EntityRef> result = createEntityStubs(prefab);
        populatePrefabEntities(prefab, result);
        return result;
    }

    private void populatePrefabEntities(Prefab prefab, Map<Name, EntityRef> result) {
        for (EntityRecipe entityRecipe : prefab.getEntityRecipes().values()) {
            EntityRef entity = result.get(entityRecipe.getIdentifier().getFragmentName());
            GeneratedFromEntityRecipeComponent entityMetadata = entity.addComponent(GeneratedFromEntityRecipeComponent.class);
            entityMetadata.setEntityRecipe(entityRecipe.getIdentifier());

            for (TypeKeyedMap.Entry<? extends Component> entry : entityRecipe.getComponents().entrySet()) {
                Component component = entity.addComponent(entry.getKey());
                componentManager.copy(entry.getValue(), component);
                processReferences(componentManager.getType(entry.getKey()), component, entityRecipe.getIdentifier(), result);
            }
        }
    }

    private void processReferences(ComponentType<?> componentType, Component component, ResourceUrn entityRecipeUrn, Map<Name, EntityRef> entityMap) {
        for (PropertyAccessor property : componentType.getPropertyInfo().getPropertiesOfType(EntityRef.class)) {
            EntityRef existing = (EntityRef) property.get(component);
            EntityRef newRef;
            if (existing instanceof EntityRecipe) {
                newRef = entityMap.get(((EntityRecipe) existing).getIdentifier().getFragmentName());
                if (newRef == null) {
                    logger.error("{} references external or unknown entity prefab {}", entityRecipeUrn, existing);
                    newRef = NullEntityRef.get();
                }
            } else if (existing instanceof PrefabRef) {
                newRef = createEntity(((PrefabRef) existing).getPrefab());
            } else {
                logger.error("{} contains unsupported entity ref {}", entityRecipeUrn, existing);
                newRef = NullEntityRef.get();
            }
            property.set(component, newRef);
        }
    }

    /**
     * Create all the entities described by a prefab.
     */
    private Map<Name, EntityRef> createEntityStubs(Prefab prefab) {
        Map<Name, EntityRef> result = Maps.newLinkedHashMap();
        for (EntityRecipe entityRecipe : prefab.getEntityRecipes().values()) {
            result.put(entityRecipe.getIdentifier().getFragmentName(), createEntity());
        }
        return result;
    }

    private boolean isActive() {
        return !transactionState.isEmpty();
    }

    // TODO: rework this to have a map of entity state rather than a table
    private static class TransactionState {
        private Table<Long, Class<? extends Component>, CacheEntry> entityCache = HashBasedTable.create();
        private Map<Long, Integer> expectedEntityRevisions = Maps.newHashMap();
        private List<NewEntityRef> createdEntities = Lists.newArrayList();
        private EntityStore entityStore;

        public TransactionState(EntityStore entityStore) {
            this.entityStore = entityStore;
        }

        @SuppressWarnings("unchecked")
        public <T extends Component> CacheEntry<T> getCacheEntry(long entityId, Class<T> componentType) {
            CacheEntry<T> cacheEntry = entityCache.get(entityId, componentType);
            if (cacheEntry == null) {
                cacheEntity(entityId);
                cacheEntry = entityCache.get(entityId, componentType);
                if (cacheEntry == null) {
                    cacheEntry = new CacheEntry<>(entityId, componentType, null, Action.NONE);
                    entityCache.put(entityId, componentType, cacheEntry);
                }
            }
            return cacheEntry;
        }

        @SuppressWarnings("unchecked")
        private void cacheEntity(long entityId) {
            if (!expectedEntityRevisions.containsKey(entityId)) {
                expectedEntityRevisions.put(entityId, entityStore.getEntityRevision(entityId));
                for (Component component : entityStore.getComponents(entityId)) {
                    entityCache.put(entityId, component.getType(), new CacheEntry(entityId, component.getType(), component, Action.UPDATE));
                }
            }
        }
    }

    /**
     * An entry in the transaction's cache of components.
     *
     * @param <T>
     */
    private static class CacheEntry<T extends Component> {
        private long entityId;
        private Class<T> componentType;
        private T component;
        private Action action;

        public CacheEntry(long entityId, Class<T> componentType, T component, Action action) {
            this.entityId = entityId;
            this.componentType = componentType;
            this.component = component;
            this.action = action;
        }

        public long getEntityId() {
            return entityId;
        }

        public Action getAction() {
            return action;
        }

        public Class<T> getComponentType() {
            return componentType;
        }

        public T getComponent() {
            return component;
        }

        public void setComponent(T component) {
            this.component = component;
        }

        public void setAction(Action action) {
            this.action = action;
        }
    }

    /**
     * The action to perform on an item in the transactin cache
     */
    private enum Action {
        NONE,
        ADD,
        UPDATE,
        REMOVE
    }


}