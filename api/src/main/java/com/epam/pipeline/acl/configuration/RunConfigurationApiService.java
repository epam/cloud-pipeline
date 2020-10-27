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

package com.epam.pipeline.acl.configuration;

import java.util.List;

import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class RunConfigurationApiService {

    @Autowired
    private RunConfigurationManager runConfigurationManager;

    @PreAuthorize("hasRole('ADMIN') OR (#configuration.parentId != null AND hasRole('CONFIGURATION_MANAGER') AND "
            + "hasPermission(#configuration.parentId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE') AND "
            + "@grantPermissionManager.hasPermissionToConfiguration(#configuration.entries, 'EXECUTE'))")
    @AclMask
    public RunConfiguration save(RunConfigurationVO configuration) {
        return runConfigurationManager.create(configuration);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'WRITE')")
    @AclMask
    public RunConfiguration update(RunConfigurationVO configuration) {
        return runConfigurationManager.update(configuration);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('CONFIGURATION_MANAGER') AND hasPermission(#id, "
            + "'com.epam.pipeline.entity.configuration.RunConfiguration', 'WRITE'))")
    public RunConfiguration delete(Long id) {
        return runConfigurationManager.delete(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#id, "
            + "'com.epam.pipeline.entity.configuration.RunConfiguration', 'READ')")
    @AclMask
    public RunConfiguration load(Long id) {
        return runConfigurationManager.load(id);
    }

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<RunConfiguration> loadAll() {
        return runConfigurationManager.loadAll();
    }
}
