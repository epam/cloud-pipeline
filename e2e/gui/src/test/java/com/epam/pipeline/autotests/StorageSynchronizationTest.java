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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.PermissionTabAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Privilege;
import com.epam.pipeline.autotests.utils.PrivilegeValue;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.DENY;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.INHERIT;

public class StorageSynchronizationTest extends AbstractBfxPipelineTest implements Navigation, StorageHandling {

    private final String syncStorage = C.SYNC_STORAGE_NAME;
    private final String syncStoragePermissions = C.SYNC_STORAGE_PERMISSIONS;

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = "EPMCMBIBPC-3232")
    public void checkStorageSynchronization() {
        if ("false".equals(C.AUTH_TOKEN)) {
            return;
        }
        navigationMenu().library();
        final String[] storagePath = syncStorage.split("/");
        Arrays.stream(storagePath).forEachOrdered(libraryContent()::cd);
        Map<Privilege, PrivilegeValue> permissions = mapPermissions(syncStoragePermissions.split(","));
        final String storageName = storagePath[storagePath.length - 1];
        libraryContent()
                .selectStorage(storageName)
                .clickEditStorageButton()
                .clickDeleteStorageButton()
                .clickUnregister()
                .validateStorageIsNotPresent(storageName);
        PermissionTabAO.UserPermissionsTableAO permissionsTableAO = waitUntilStorageAppears(storageName, C.SYNC_STORAGE_TIMEOUT * 1000L)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .selectByName(C.SYNC_STORAGE_PERMISSION_NAME)
                .showPermissions();
        permissions.forEach(permissionsTableAO::validatePrivilegeValue);
        permissionsTableAO
                .closeAll();
    }

    private Map<Privilege, PrivilegeValue> mapPermissions(final String[] permissions) {
        final List<String> permits = Arrays.asList("r", "w", "e");
        final List<String> actions = Arrays.asList("a", "d");
        return Arrays.stream(permissions).map(p -> {
            String[] permit = p.split(":");
            if (permit.length != 2
                    || !permits.contains(permit[0].toLowerCase())
                    || !actions.contains(permit[1].toLowerCase())) {
                throw new IllegalArgumentException(
                        "Synchronization storage permission is empty or presented in the wrong format. " +
                                "Specify 'e2e.ui.sync.storage.permissions' as e.g. 'r:a,w:d,e:a'");
            }
            switch (permit[0].toLowerCase()) {
                case "r":
                    return Pair.of(READ, getPrivilegeValue(permit[1]));
                case "w":
                    return Pair.of(WRITE, getPrivilegeValue(permit[1]));
                default:
                    return Pair.of(EXECUTE, getPrivilegeValue(permit[1]));
            }
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private PrivilegeValue getPrivilegeValue(final String permission) {
        if ("a".equalsIgnoreCase(permission)) {
            return ALLOW;
        }
        if ("d".equalsIgnoreCase(permission)) {
            return DENY;
        }
        return INHERIT;
    }

    private PipelinesLibraryAO libraryContent() {
        return new PipelinesLibraryAO();
    }
}
