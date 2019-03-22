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


public class ToolPermission implements Permission, Navigation {
    private final String toolName;
    private final String group;
    private final Privilege privilege;
    private final PrivilegeValue value;
    private final String registry;

    private ToolPermission(Privilege privilege, String toolName, PrivilegeValue value, String registry, String group) {
        this.privilege = privilege;
        this.toolName = toolName;
        this.value = value;
        this.registry = registry;
        this.group = group;
    }

    public static ToolPermission allow(Privilege privilege, String toolName, String registry, String library) {
        return new ToolPermission(privilege, toolName, ALLOW, registry, library);
    }

    public static ToolPermission deny(Privilege privilege, String toolName, String registry, String library) {
        return new ToolPermission(privilege, toolName, DENY, registry, library);
    }

    public static ToolPermission inherit(Privilege privilege, String toolName, String registry, String library) {
        return new ToolPermission(privilege, toolName, INHERIT, registry, library);
    }

    @Override
    public void set(String username) {
        tools()
                .performWithin(registry, group, toolName, tool ->
                        tool.permissions(permission ->
                                permission
                                        .selectByName(username)
                                        .showPermissions()
                                        .set(privilege, value)
                                        .closeAll())
                );
    }
}