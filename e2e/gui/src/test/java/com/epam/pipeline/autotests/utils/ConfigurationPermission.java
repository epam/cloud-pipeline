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
package com.epam.pipeline.autotests.utils;

import com.epam.pipeline.autotests.ao.PermissionTabAO;
import com.epam.pipeline.autotests.mixins.Navigation;

import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.DENY;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.INHERIT;

public class ConfigurationPermission implements Permission, Navigation {

    private final String configName;
    private final Privilege privilege;
    private final PrivilegeValue value;

    private ConfigurationPermission(Privilege privilege, String configName, PrivilegeValue value) {
        this.privilege = privilege;
        this.configName = configName;
        this.value = value;
    }

    public static ConfigurationPermission allow(final Privilege privilege, final String configName) {
        return new ConfigurationPermission(privilege, configName, ALLOW);
    }

    public static ConfigurationPermission deny(final Privilege privilege, final String configName) {
        return new ConfigurationPermission(privilege, configName, DENY);
    }

    public static ConfigurationPermission inherit(final Privilege privilege, final String configName) {
        return new ConfigurationPermission(privilege, configName, INHERIT);
    }
    
    @Override
    public void set(final String username) {
        library()
                .configurationWithin(configName, configuration -> 
                        configuration
                                .edit(conf -> {
                                    PermissionTabAO.PermissionTabElementAO permissionTabElementAO = conf.permission()
                                            .selectByName(username);
                                    privilege.setTo(value);
                                    permissionTabElementAO.closeAll();
                                }));
    }
}
