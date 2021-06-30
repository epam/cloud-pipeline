/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.StorageContentAO;

import java.util.Arrays;

import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.Selenide.screenshot;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.titleOfTreeItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.treeItem;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;


public interface StorageHandling extends Navigation {

    default StorageContentAO createStorage(String storageName) {
        return library()
                .createStorage(storageName)
                .selectStorage(storageName);
    }

    default PipelinesLibraryAO removeStorage(String storageName) {
        return library().removeStorage(storageName);
    }

    default PipelinesLibraryAO removeStorage(String storageName, String... folders) {
        PipelinesLibraryAO library = library();

        Arrays.stream(folders).forEachOrdered(library::cd);
        return library.removeStorage(storageName);
    }

    default StorageContentAO waitUntilStorageAppears(final String storageName, final long timeout) {
        int minutes = Math.toIntExact(timeout / 60);
        for (int i = 0; i <= minutes; i++) {
            sleep(10, SECONDS);
            if ($(byId("pipelines-library-tree-container")).find(titleOfTreeItem(treeItem(storageName))).exists()) {
                break;
            }
            if (i == minutes) {
                screenshot("failed_sync_storage_" + storageName);
                throw new IllegalArgumentException(format("Synchronized storage %s is not appeared", storageName));
            }
            sleep(1, MINUTES);
            refresh();
            sleep(5, SECONDS);
        }
        return library().selectStorage(storageName);
    }
}
