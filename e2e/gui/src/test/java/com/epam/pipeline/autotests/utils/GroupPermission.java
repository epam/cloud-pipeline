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

import com.epam.pipeline.autotests.mixins.Navigation;

import static com.epam.pipeline.autotests.utils.PrivilegeValue.*;

public class GroupPermission implements Permission, Navigation {
    private final Privilege privilege;
    private final PrivilegeValue value;
    private final String registry;
    private final String group;

    private GroupPermission(Privilege privilege, String registry, String group, PrivilegeValue value) {
        this.privilege = privilege;
        this.value = value;
        this.registry = registry;
        this.group = group;
    }

    public static GroupPermission allow(Privilege privilege, String registry, String group) {
        return new GroupPermission(privilege, registry, group, ALLOW);
    }

    public static GroupPermission deny(Privilege privilege, String registry, String group) {
        return new GroupPermission(privilege, registry, group,  DENY);
    }

    public static GroupPermission inherit(Privilege privilege, String registry, String group) {
        return new GroupPermission(privilege, registry, group, INHERIT);
    }

    @Override
    public void set(String username) {
        tools().performWithin(registry, group, group ->
                group.editGroup(settings ->
                        settings.permissions()
                        .selectByName(username)
                        .showPermissions()
                        .set(privilege, value)
                        .closeAll()
                )
        );

    }

}
