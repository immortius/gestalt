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

package org.terasology.util.io;

import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Immortius
 */
public class FileExtensionPathMatcher implements PathMatcher {

    private final Set<String> extensions;

    public FileExtensionPathMatcher(String ... extensions) {
        this(Arrays.asList(extensions));
    }

    public FileExtensionPathMatcher(Collection<String> extensions) {
        this.extensions = Sets.newHashSet(extensions);
    }

    @Override
    public boolean matches(Path path) {
        return extensions.contains(Files.getFileExtension(path.getFileName().toString()));
    }
}
