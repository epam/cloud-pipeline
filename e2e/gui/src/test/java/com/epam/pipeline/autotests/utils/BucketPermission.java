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
package com.epam.pipeline.autotests.utils;

import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PermissionTabAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;

import java.util.Arrays;

import static com.epam.pipeline.autotests.utils.PrivilegeValue.*;


public class BucketPermission implements Permission {
    private final String bucketName;
    private final Privilege privilege;
    private final PrivilegeValue value;
    private final String[] folders;

    private BucketPermission(Privilege privilege, String bucketName, PrivilegeValue value, String... folders) {
        this.privilege = privilege;
        this.bucketName = bucketName;
        this.value = value;
        this.folders = folders;
    }

    public static BucketPermission allow(Privilege privilege, String bucketName, String... folders) {
        return new BucketPermission(privilege, bucketName, ALLOW, folders);
    }

    public static BucketPermission deny(Privilege privilege, String bucketName, String... folders) {
        return new BucketPermission(privilege, bucketName, DENY, folders);
    }

    public static BucketPermission inherit(Privilege privilege, String bucketName, String... folders) {
        return new BucketPermission(privilege, bucketName, INHERIT, folders);
    }

    @Override
    public void set(String username) {
        PipelinesLibraryAO library = new NavigationMenuAO().library();
        Arrays.stream(folders).forEachOrdered(library::cd);

        PermissionTabAO.UserPermissionsTableAO userPermissionsTableAO =
                library.selectStorage(bucketName)
                        .clickEditStorageButton()
                        .clickOnPermissionsTab()
                        .selectByName(username)
                        .showPermissions();

        privilege.setTo(value);
        userPermissionsTableAO.closeAll();

    }
}
