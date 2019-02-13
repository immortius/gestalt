/*
 * Copyright 2019 MovingBlocks
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

package org.terasology.assets.module.annotations;

import org.terasology.assets.module.ModuleAwareAssetTypeManager;
import org.terasology.module.sandbox.API;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link org.terasology.assets.format.AssetFileFormat AssetFileFormat} to be automatically registered by
 * {@link ModuleAwareAssetTypeManager ModuleAwareAssetTypeManager} on environment change to handle loading assets and asset overrides from files.
 * <p>
 * By default the AssetFileFormat must have an empty constructor, or one taking an AssetManager
 * </p>
 * @author Immortius
 */
@API
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterAssetFileFormat {
}
