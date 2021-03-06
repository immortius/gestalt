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

package virtualModules.test.stubs.extensions;

import org.terasology.gestalt.assets.format.AbstractAssetAlterationFileFormat;
import org.terasology.gestalt.assets.format.AssetDataFile;
import org.terasology.gestalt.assets.module.annotations.RegisterAssetSupplementalFileFormat;

import java.io.IOException;

import virtualModules.test.stubs.text.TextData;

/**
 * @author Immortius
 */
@RegisterAssetSupplementalFileFormat
public class ExtensionSupplementalFileFormat extends AbstractAssetAlterationFileFormat<TextData> {

    public ExtensionSupplementalFileFormat() {
        super("moo");
    }

    @Override
    public void apply(AssetDataFile input, TextData assetData) throws IOException {

    }
}
