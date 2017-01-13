/*
 * Copyright 2015 MovingBlocks
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

package org.terasology.entitysystem.index;

import com.google.common.collect.Sets;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import org.terasology.entitysystem.core.Component;
import org.terasology.entitysystem.core.EntityManager;
import org.terasology.entitysystem.core.EntityRef;
import org.terasology.entitysystem.entity.inmemory.EntityState;
import org.terasology.entitysystem.entity.inmemory.EntitySystemState;
import org.terasology.entitysystem.entity.inmemory.NewEntityRef;
import org.terasology.entitysystem.entity.inmemory.NewEntityState;
import org.terasology.entitysystem.transaction.TransactionManager;
import org.terasology.entitysystem.transaction.pipeline.TransactionContext;
import org.terasology.entitysystem.transaction.pipeline.TransactionInterceptor;
import org.terasology.entitysystem.transaction.pipeline.TransactionStage;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public class ComponentIndex implements TransactionInterceptor, Iterable<EntityRef> {

    private EntityManager entityManager;
    private Class<? extends Component> componentType;
    private TLongSet entities = new TLongHashSet();

    public ComponentIndex(TransactionManager transactionManager, EntityManager entityManager, Class<? extends Component> componentType) {
        transactionManager.getPipeline().registerInterceptor(TransactionStage.UPDATE_INDEXES, this);
        this.componentType = componentType;
        this.entityManager = entityManager;
    }

    @Override
    public void handle(TransactionContext context) {
        Optional<EntitySystemState> entityState = context.getAttachment(EntitySystemState.class);
        if (entityState.isPresent()) {
            for (NewEntityState state : entityState.get().getNewEntities()) {
                if (state.getComponents().containsKey(componentType)) {
                    entities.add(state.getId());
                }
            }
            for (EntityState state : entityState.get().getEntityStates()) {
                switch (state.getUpdateAction(componentType)) {
                    case ADD:
                        entities.add(state.getId());
                        break;
                    case REMOVE:
                        entities.remove(state.getId());
                        break;
                }
            }
        }
    }

    public boolean contains(EntityRef entity) {
        return entities.contains(entity.getId());
    }

    @Override
    public Iterator<EntityRef> iterator() {
        return entityManager.getEntities(entities).iterator();
    }
}