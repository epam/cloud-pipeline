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

import static com.epam.pipeline.autotests.utils.PrivilegeValue.*;

public class FolderPermission implements Permission {
    private final String folderName;
    private final Privilege privilege;
    private final PrivilegeValue value;

    private FolderPermission(Privilege privilege, String folderName, PrivilegeValue value) {
        this.privilege = privilege;
        this.folderName = folderName;
        this.value = value;
    }

    public static FolderPermission allow(Privilege privilege, String folderName) {
        return new FolderPermission(privilege, folderName, ALLOW);
    }

    public static FolderPermission deny(Privilege privilege, String folderName) {
        return new FolderPermission(privilege, folderName, DENY);
    }

    public static FolderPermission inherit(Privilege privilege, String folderName) {
        return new FolderPermission(privilege, folderName, INHERIT);
    }

    @Override
    public void set(String username) {
        PermissionTabAO.UserPermissionsTableAO userPermissionsTableAO = new NavigationMenuAO()
                .library()
                .clickOnFolder(folderName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .selectByName(username)
                .showPermissions();

        privilege.setTo(value);
        userPermissionsTableAO.closeAll();
    }
}
