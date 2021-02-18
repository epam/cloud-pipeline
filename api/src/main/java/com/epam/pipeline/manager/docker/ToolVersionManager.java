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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.pipeline.ToolManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ToolVersionManager {
    private final ToolVersionDao toolVersionDao;
    private final ToolManager toolManager;
    private final MessageHelper messageHelper;

    /**
     * Updates existing tool version according to it's state on docker registry. If required tool version
     * does not exist a new one will be created.
     * @param toolId tool ID
     * @param version tool version (tag)
     * @param imageName the name of the image
     * @param registry docker registry
     * @param dockerClient docker client
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateOrCreateToolVersion(final Long toolId, final String version, final String imageName,
                                          final DockerRegistry registry, final DockerClient dockerClient) {
        validateToolExistsAndCanBeModified(toolId);
        Optional<ToolVersion> toolVersion = toolVersionDao.loadToolVersion(toolId, version);
        ToolVersion versionAttributes = dockerClient.getVersionAttributes(registry, imageName, version);
        versionAttributes.setToolId(toolId);
        if (toolVersion.isPresent()) {
            versionAttributes.setId(toolVersion.get().getId());
            toolVersionDao.updateToolVersion(versionAttributes);
        } else {
            toolVersionDao.createToolVersion(versionAttributes);
        }
    }

    /**
     * Deletes all tool versions related to specified tool ID.
     * @param toolId tool ID
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteToolVersions(final Long toolId) {
        validateToolExistsAndCanBeModified(toolId);
        toolVersionDao.deleteToolVersions(toolId);
    }

    /**
     * Deletes specific tool version determined by tool ID and it's version.
     * @param toolId tool ID
     * @param version tool version (tag)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteToolVersion(final Long toolId, final String version) {
        validateToolExistsAndCanBeModified(toolId);
        toolVersionDao.deleteToolVersion(toolId, version);
    }

    /**
     * Loads tool version attributes by tool ID and it's version.
     * @param toolId tool ID
     * @param version tool version (tag)
     */
    public ToolVersion loadToolVersion(final Long toolId, final String version) {
        return toolVersionDao.loadToolVersion(toolId, version).orElse(null);
    }

    /**
     * Loads tool version attributes for a tool ID and list of versions
     * @param toolId
     * @param versions
     * @return
     */
    public Map<String, ToolVersion> loadToolVersions(final Long toolId, final List<String> versions) {
        return toolVersionDao.loadToolVersions(toolId, versions);
    }

    /**
     * Creates settings for specific tool version.
     * @param toolId tool ID
     * @param version tool version (tag)
     * @param settings list of tool version settings
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ToolVersion createToolVersionSettings(final Long toolId, final String version,
                                                 final List<ConfigurationEntry> settings) {
        validateToolExistsAndCanBeModified(toolId);
        Optional<ToolVersion> toolVersion = toolVersionDao.loadToolVersion(toolId, version);
        ToolVersion toolVersionWithSettings;
        if (toolVersion.isPresent()) {
            toolVersionWithSettings = toolVersion.get();
            toolVersionWithSettings.setSettings(settings);
            toolVersionDao.updateToolVersionWithSettings(toolVersionWithSettings);
        } else {
            toolVersionWithSettings = ToolVersion
                    .builder()
                    .toolId(toolId)
                    .version(version)
                    .settings(settings)
                    .build();
            toolVersionDao.createToolVersionWithSettings(toolVersionWithSettings);
        }
        return toolVersionWithSettings;
    }

    /**
     * Loads settings specified by tool ID and it's version.
     * @param toolId tool ID
     * @param version tool version (tag)
     */
    public List<ToolVersion> loadToolVersionSettings(final Long toolId, final String version) {
        if (StringUtils.hasText(version)) {
            return toolVersionDao.loadToolVersionWithSettings(toolId, version)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }
        return toolVersionDao.loadToolWithSettings(toolId);
    }

    private void validateToolExistsAndCanBeModified(final Long toolId) {
        final Tool tool = toolManager.load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
    }

    private void validateToolNotNull(final Tool tool, final Long toolId) {
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, toolId));
    }

    private void validateToolCanBeModified(final Tool tool) {
        Assert.isTrue(tool.isNotSymlink(), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_SYMLINK_MODIFICATION_NOT_SUPPORTED));
    }

}
