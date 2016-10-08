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

package org.terasology.entitysystem.jmh;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.terasology.entitysystem.component.CodeGenComponentManager;
import org.terasology.entitysystem.core.EntityManager;
import org.terasology.entitysystem.transaction.inmemory.InMemoryEntityManager;
import org.terasology.valuetype.ImmutableCopy;
import org.terasology.valuetype.TypeHandler;
import org.terasology.valuetype.TypeLibrary;

import java.net.URL;
import java.net.URLClassLoader;

/**
 *
 */
public class EntitySystemBenchmark {


    @State(Scope.Benchmark)
    public static class EntitySystem {
        public EntityManager entityManager;
        public URLClassLoader tempLoader;
        public TypeLibrary typeLibrary;

        public long entityId = 1;

        public EntitySystem() {
            typeLibrary = new TypeLibrary();
            typeLibrary.addHandler(new TypeHandler<>(String.class, ImmutableCopy.create()));
            tempLoader = new URLClassLoader(new URL[0]);
            entityManager = new InMemoryEntityManager(new CodeGenComponentManager(typeLibrary));
        }

        @TearDown
        public void teardown() {
            entityManager = new InMemoryEntityManager(new CodeGenComponentManager(typeLibrary));
        }
    }




}