/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.autotests.ao.Primitive.ALL_STORAGES;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class StorageBackupsTest extends AbstractBfxPipelineTest implements Navigation, StorageHandling {

    private final List<String> backupStorageNames = C.BACKUP_STORAGE_NAMES;
    private final String backupStoragePath = C.BACKUP_STORAGE_PATH;
    private final int backupTimeOffset = C.BACKUP_STORAGE_OFFSET;
    private final String cpApiFolder = "cp-api-srv";
    private final String cpGitFolder = "cp-git";
    private final String cpDBFolder = "cp-api-db";

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = "TC-DATASTORAGES-2")
    public void checkBackupStorages() {
        if ("false".equals(C.AUTH_TOKEN)) {
            return;
        }
        final String currentDate = LocalDateTime.now(ZoneOffset.UTC).minusHours(backupTimeOffset)
                .format(DateTimeFormatter.ofPattern(Utils.DATE_PATTERN));
        final String date = currentDate.replace("-", "");
        final Map<String, List<String>> backups = new HashMap<>();
        backups.put(cpApiFolder, Collections.singletonList(format("cp-bkp-api-settings-dump-%s.tgz", date)));
        backups.put(cpGitFolder, Arrays.asList(
                format("cp-bkp-git-settings-dump-%s.tgz", date),
                format("cp-bkp-\\d+_%s_9.4.0_gitlab_backup.tar", currentDate.replace("-", "_"))));
        backups.put(cpDBFolder, Collections.singletonList(format("cp-bkp-api-db-dump-%s.sql.gz", date)));
        final String[] storagePath = backupStoragePath.split("/");
        Map<String, String> backupSizeMap = new HashMap<>(10);
        backupStorageNames.forEach(storage -> {
            final StorageContentAO storageContentAO = navigationMenu()
                    .library()
                    .ensure(ALL_STORAGES, Condition.visible)
                    .click(ALL_STORAGES)
                    .openStorageFromTable(storage);
            Arrays.stream(storagePath).forEachOrdered(storageContentAO::cd);
            backups.keySet().forEach(key -> {
                storageContentAO
                        .cd(key)
                        .cd(currentDate);
                backups.get(key).forEach(entity -> {
                    storageContentAO.validateElementByPatternIsPresent(entity);
                    checkBackupSizeIfNeeded(backupSizeMap, storageContentAO, entity);
                });
                storageContentAO.cd("..").cd("..");
            });
        });
    }

    private void checkBackupSizeIfNeeded(final Map<String, String> backupSizeMap,
                                         final StorageContentAO storageContentAO,
                                         final String file) {
        if (backupStorageNames.size() <= 1) {
            return;
        }
        final String fileSize = storageContentAO.findFileSizeByPattern(file);
        assertNotEquals(fileSize, "0 bytes", format("The %s file is empty", file));
        if (!backupSizeMap.isEmpty() && backupSizeMap.containsKey(file)) {
            assertEquals(backupSizeMap.get(file), fileSize,
                    format("The %s file size doesn't correspond the file size in another storage", fileSize));
            return;
        }
        backupSizeMap.put(file, fileSize);
    }
}
