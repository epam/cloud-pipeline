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

public class RegistryPermission implements Permission, Navigation {
    private final Privilege privilege;
    private final PrivilegeValue value;
    private final String registry;

    private RegistryPermission(Privilege privilege, String registry, PrivilegeValue value) {
        this.privilege = privilege;
        this.value = value;
        this.registry = registry;
    }

    public static RegistryPermission allow(Privilege privilege, String registry) {
        return new RegistryPermission(privilege, registry, ALLOW);
    }

    public static RegistryPermission deny(Privilege privilege, String registry) {
        return new RegistryPermission(privilege, registry, DENY);
    }

    public static RegistryPermission inherit(Privilege privilege, String registry) {
        return new RegistryPermission(privilege, registry, INHERIT);
    }

    @Override
    public void set(String username) {
        tools().editRegistry(registry, settings ->
                settings.permissions()
                        .selectByName(username)
                        .showPermissions()
                        .set(privilege, value)
                        .closeAll());
    }

}
