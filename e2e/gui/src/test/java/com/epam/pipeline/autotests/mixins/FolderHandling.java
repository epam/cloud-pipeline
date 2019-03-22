/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.pipeline.autotests.mixins;

import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;

import java.util.Arrays;

public interface FolderHandling extends Navigation {

    default PipelinesLibraryAO createFolder(String folderName) {
        return library()
                .createFolder(folderName)
                .cd(folderName);
    }

    default PipelinesLibraryAO removeFolder(String root, String... folders) {
        PipelinesLibraryAO library = library().cd(root);

        Arrays.stream(folders).forEachOrdered(library::cd);
        return library.removeFolder(folders.length == 0 ? root : folders[folders.length - 1]);
    }
}
