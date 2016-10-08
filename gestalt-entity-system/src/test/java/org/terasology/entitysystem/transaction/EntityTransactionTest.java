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

package org.terasology.entitysystem.transaction;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.terasology.entitysystem.component.CodeGenComponentManager;
import org.terasology.entitysystem.core.EntityManager;
import org.terasology.entitysystem.core.EntityRef;
import org.terasology.entitysystem.transaction.inmemory.InMemoryEntityManager;
import org.terasology.entitysystem.transaction.references.CoreEntityRef;
import org.terasology.entitysystem.transaction.references.NewEntityRef;
import org.terasology.entitysystem.transaction.references.NullEntityRef;
import org.terasology.entitysystem.stubs.SampleComponent;
import org.terasology.entitysystem.stubs.SecondComponent;
import org.terasology.valuetype.ImmutableCopy;
import org.terasology.valuetype.TypeHandler;
import org.terasology.valuetype.TypeLibrary;

import java.util.ConcurrentModificationException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class EntityTransactionTest {
    private static final String TEST_NAME = "Fred";
    private static final String TEST_NAME_2 = "Jill";
    private EntityManager entityManager;

    public EntityTransactionTest() {
        TypeLibrary typeLibrary = new TypeLibrary();
        typeLibrary.addHandler(new TypeHandler<>(String.class, ImmutableCopy.create()));
        entityManager = new InMemoryEntityManager(new CodeGenComponentManager(typeLibrary));
    }

    @Test
    public void createEntityAddsCoreEntityRefOnCommit() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        SampleComponent sampleComponent = entity.addComponent(SampleComponent.class);
        sampleComponent.setName(TEST_NAME);
        entityManager.commit();

        assertTrue(entity instanceof NewEntityRef);
        NewEntityRef newEntityRef = (NewEntityRef) entity;
        assertTrue(newEntityRef.getInnerEntityRef().isPresent());
        assertTrue(newEntityRef.getInnerEntityRef().get() instanceof CoreEntityRef);
    }

    @Test
    public void createEmptyEntityAddsNullEntityRefOnCommit() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entityManager.commit();

        assertTrue(entity instanceof NewEntityRef);
        NewEntityRef newEntityRef = (NewEntityRef) entity;
        assertTrue(newEntityRef.getInnerEntityRef().isPresent());
        assertEquals(NullEntityRef.get(), newEntityRef.getInnerEntityRef().get());
    }

    @Test
    public void createEntityAddsInnerNullEntityRefOnRollback() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        SampleComponent sampleComponent = entity.addComponent(SampleComponent.class);
        sampleComponent.setName(TEST_NAME);
        entityManager.rollback();

        assertTrue(entity instanceof NewEntityRef);
        NewEntityRef newEntityRef = (NewEntityRef) entity;
        assertTrue(newEntityRef.getInnerEntityRef().isPresent());
        assertEquals(NullEntityRef.get(), newEntityRef.getInnerEntityRef().get());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void createEntityAddsInnerNullEntityRefOnFailedCommit() throws Exception {
        entityManager.beginTransaction();
        EntityRef initialEntity = entityManager.createEntity();
        initialEntity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        initialEntity.delete();
        EntityRef entity = entityManager.createEntity();
        SampleComponent sampleComponent = entity.addComponent(SampleComponent.class);
        sampleComponent.setName(TEST_NAME);
        entityManager.beginTransaction();
        initialEntity.addComponent(SecondComponent.class);
        entityManager.commit();
        try {
            entityManager.commit();
        } finally {
            assertTrue(entity instanceof NewEntityRef);
            NewEntityRef newEntityRef = (NewEntityRef) entity;
            assertTrue(newEntityRef.getInnerEntityRef().isPresent());
            assertEquals(NullEntityRef.get(), newEntityRef.getInnerEntityRef().get());
        }
    }

    @Test
    public void addThenRemoveComponent() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        entity.addComponent(SecondComponent.class);
        entity.removeComponent(SecondComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        Optional<SecondComponent> finalComp = entity.getComponent(SecondComponent.class);
        assertFalse(finalComp.isPresent());
    }

    @Test
    public void removeThenAddComponent() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        entity.removeComponent(SampleComponent.class);
        SampleComponent comp = entity.addComponent(SampleComponent.class);
        comp.setName(TEST_NAME);
        entityManager.commit();

        entityManager.beginTransaction();
        Optional<SampleComponent> finalComp = entity.getComponent(SampleComponent.class);
        assertTrue(finalComp.isPresent());
        assertEquals(TEST_NAME, finalComp.get().getName());
    }

    @Test
    public void rollback() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        entity.removeComponent(SampleComponent.class);
        SecondComponent comp = entity.addComponent(SecondComponent.class);
        comp.setName(TEST_NAME);
        entityManager.rollback();

        entityManager.beginTransaction();
        Optional<SampleComponent> finalSampleComp = entity.getComponent(SampleComponent.class);
        assertTrue(finalSampleComp.isPresent());
        Optional<SecondComponent> finalSecondComp = entity.getComponent(SecondComponent.class);
        assertFalse(finalSecondComp.isPresent());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void concurrentModificationTriggersException() {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        SampleComponent transactionComponent = entity.getComponent(SampleComponent.class).get();
        transactionComponent.setName(TEST_NAME);

        entityManager.beginTransaction();
        entity.getComponent(SampleComponent.class).get().setName(TEST_NAME_2);
        entityManager.commit();

        entityManager.commit();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void failedCommitIsRolledBack() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        SampleComponent transactionComponent = entity.getComponent(SampleComponent.class).get();
        transactionComponent.setName(TEST_NAME);
        entity.addComponent(SecondComponent.class);

        entityManager.beginTransaction();
        entity.getComponent(SampleComponent.class).get().setName(TEST_NAME_2);
        entityManager.commit();

        try {
            entityManager.commit();
        } finally {
            entityManager.beginTransaction();
            assertEquals(TEST_NAME_2, entity.getComponent(SampleComponent.class).get().getName());
            assertFalse(entity.getComponent(SecondComponent.class).isPresent());
        }

    }

    @Test
    public void getCompositionOfEntity() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SampleComponent.class);
        entity.addComponent(SecondComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        assertEquals(Sets.newHashSet(SampleComponent.class, SecondComponent.class), entity.getComponentTypes());
    }

    @Test
    public void getCompositionOfEntityAccountsForLocalModification() throws Exception {
        entityManager.beginTransaction();
        EntityRef entity = entityManager.createEntity();
        entity.addComponent(SecondComponent.class);
        entity.addComponent(SampleComponent.class);
        entityManager.commit();

        entityManager.beginTransaction();
        entity.removeComponent(SampleComponent.class);
        assertEquals(Sets.newHashSet(SecondComponent.class), entity.getComponentTypes());
    }

    @Test
    public void transactionInactiveIfNotStartted() {
        assertFalse(entityManager.isTransactionActive());
    }


}