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


public class PipelinePermission implements Permission {
    private final String pipelineName;
    private final Privilege privilege;
    private final PrivilegeValue value;

    private PipelinePermission(Privilege privilege, String pipelineName, PrivilegeValue value) {
        this.privilege = privilege;
        this.pipelineName = pipelineName;
        this.value = value;
    }

    public static PipelinePermission allow(Privilege privilege, String pipelineName) {
        return new PipelinePermission(privilege, pipelineName, ALLOW);
    }

    public static PipelinePermission deny(Privilege privilege, String pipelineName) {
        return new PipelinePermission(privilege, pipelineName, DENY);
    }

    public static PipelinePermission inherit(Privilege privilege, String pipelineName) {
        return new PipelinePermission(privilege, pipelineName, INHERIT);
    }

    @Override
    public void set(String username) {
        PermissionTabAO.UserPermissionsTableAO userPermissionsTableAO = new NavigationMenuAO()
                .library()
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .selectByName(username)
                .showPermissions();

        privilege.setTo(value);
        userPermissionsTableAO.closeAll();
    }
}