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

package org.terasology.gestalt.module.resources;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * EmptyFileSource, a null object for when no file source is desired.
 */
public class EmptyFileSource implements ModuleFileSource {

    @Override
    public Optional<FileReference> getFile(List<String> filepath) {
        return Optional.empty();
    }

    @Override
    public Collection<FileReference> getFilesInPath(boolean recursive, List<String> path) {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getSubpaths(List<String> fromPath) {
        return Collections.emptySet();
    }

    @Override
    public Iterator<FileReference> iterator() {
        return Collections.emptyIterator();
    }
}
