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

package com.epam.pipeline.acl.pipeline;

import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_WRITE;

import java.util.List;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PipelineConfigApiService {

    @Autowired
    private PipelineVersionManager versionManager;

    @PreAuthorize(PIPELINE_ID_READ)
    public List<ConfigurationEntry> loadConfigurations(Long id, String version) throws
            GitClientException {
        return versionManager.loadConfigurationsFromScript(id, version);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public List<ConfigurationEntry> addConfiguration(Long id, ConfigurationEntry configuration)
            throws GitClientException {
        return versionManager.addConfiguration(id, configuration);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public List<ConfigurationEntry> deleteConfiguration(Long id, String configName)
            throws GitClientException {
        return versionManager.deleteConfiguration(id, configName);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public PipelineConfiguration loadParametersFromScript(Long id, String version, String configName)
            throws GitClientException {
        return versionManager.loadParametersFromScript(id, version, configName);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public List<ConfigurationEntry> renameConfiguration(Long id, String oldName, String newName)
            throws GitClientException {
        return versionManager.renameConfiguration(id, oldName, newName);
    }
}
