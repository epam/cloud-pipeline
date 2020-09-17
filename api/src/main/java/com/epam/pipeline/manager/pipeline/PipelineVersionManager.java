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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.exception.ConfigDecodingException;
import com.epam.pipeline.exception.ConfigurationReadingException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.python.GraphReader;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PipelineVersionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineVersionManager.class);
    private static final String CONFIG_FILE_NAME = "config.json";

    @Autowired private GitManager gitManager;

    @Autowired private PipelineManager pipelineManager;

    @Autowired private ToolManager toolManager;

    @Autowired private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private PipelineConfigurationPostProcessor postProcessor;

    private JsonMapper mapper = new JsonMapper();

    @Value("${luigi.graph.script}")
    private String graphScript;

    @PostConstruct public void init() {
        mapper.setSerializationInclusion(Include.NON_EMPTY)
                .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<Revision> loadAllVersionFromGit(Long id) throws GitClientException {
        Pipeline pipeline = pipelineManager.load(id);
        return gitManager.getPipelineRevisions(pipeline);
    }

    public TaskGraphVO getWorkflowGraph(Long id, String version) {
        Pipeline pipeline = pipelineManager.load(id);
        try {
            gitManager.loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
        File config = gitManager.getConfigFile(pipeline, version);
        TaskGraphVO result = new GraphReader()
                .readGraph(graphScript, config.getParentFile().getAbsolutePath(), CONFIG_FILE_NAME);
        mergeToolsRequirements(result);
        try {
            FileUtils.deleteDirectory(config.getParentFile());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return result;
    }

    public PipelineConfiguration loadParametersFromScript(Long id, String version)
            throws GitClientException {
        return loadParametersFromScript(id, version, null);
    }

    public ConfigurationEntry loadConfigurationEntry(Long id, String version, String configName)
            throws GitClientException {
        List<ConfigurationEntry> configurations = loadConfigurationsFromScript(id, version);
        if (CollectionUtils.isEmpty(configurations)) {
            throw new ConfigurationReadingException(CONFIG_FILE_NAME);
        }
        if (StringUtils.hasText(configName)) {
            return configurations.stream()
                    .filter(conf -> conf.getName() != null && conf.getName().equals(configName))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(messageHelper
                            .getMessage(MessageConstants.ERROR_CONFIG_NOT_FOUND, configName)));
        }
        ConfigurationEntry defaultConfig = getDefaultConfig(configurations);
        return defaultConfig == null ? configurations.get(0) : defaultConfig;
    }

    public PipelineConfiguration loadParametersFromScript(Long id, String version, String configName)
            throws GitClientException {
        return loadConfigurationEntry(id, version, configName).getConfiguration();
    }

    public List<ConfigurationEntry> loadConfigurationsFromScript(Long id, String version)
            throws GitClientException {
        Pipeline pipeline = pipelineManager.load(id);
        return getPipelineConfigurations(pipeline, version);
    }

    public List<ConfigurationEntry> addConfiguration(Long id, ConfigurationEntry configuration)
            throws GitClientException {
        Assert.isTrue(configuration.checkConfigComplete(),
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_INVALID));
        String configurationName = configuration.getName();
        Pipeline pipeline = pipelineManager.load(id, true);
        List<ConfigurationEntry> currentConfigurations = getCurrentConfigurations(pipeline);
        checkDefaultConfig(configuration, currentConfigurations);
        List<ConfigurationEntry> updatedConf = removeConfig(configurationName, currentConfigurations);
        updatedConf.add(configuration);
        String message =
                messageHelper.getMessage(MessageConstants.INFO_CONFIG_UPDATE, configurationName);
        return saveUpdatedConfiguration(configurationName, pipeline, updatedConf, message);
    }

    public List<ConfigurationEntry> deleteConfiguration(Long id, String configName)
            throws GitClientException {
        Assert.notNull(configName, messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NAME_REQUIRED));
        Pipeline pipeline = pipelineManager.load(id, true);
        List<ConfigurationEntry> currentConfigurations = getCurrentConfigurations(pipeline);
        List<ConfigurationEntry> updatedConf = removeConfig(configName, currentConfigurations);
        Assert.isTrue(currentConfigurations.size() != updatedConf.size(),
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NOT_FOUND, configName));
        String message = messageHelper.getMessage(MessageConstants.INFO_CONFIG_DELETE, configName);
        return saveUpdatedConfiguration(configName, pipeline, updatedConf, message);
    }

    public List<ConfigurationEntry> renameConfiguration(Long id, String oldName, String newName)
            throws GitClientException {
        Assert.isTrue(StringUtils.hasText(oldName),
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NAME_REQUIRED));
        Assert.isTrue(StringUtils.hasText(newName),
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NAME_REQUIRED));
        Pipeline pipeline = pipelineManager.load(id, true);
        List<ConfigurationEntry> currentConfigurations = getCurrentConfigurations(pipeline);
        ConfigurationEntry oldConfig = findConfigByName(currentConfigurations, oldName);
        Assert.notNull(oldConfig,
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NOT_FOUND, oldName));
        Assert.isTrue(findConfigByName(currentConfigurations, newName) == null,
                messageHelper.getMessage(MessageConstants.ERROR_CONFIG_NAME_EXISTS, newName));
        currentConfigurations.forEach(config -> {
            if (oldName.equals(config.getName())) {
                config.setName(newName);
            }
        });
        String message = messageHelper.getMessage(MessageConstants.INFO_CONFIG_RENAME, oldName, newName);
        return saveUpdatedConfiguration(newName, pipeline, currentConfigurations, message);
    }

    private ConfigurationEntry findConfigByName(List<ConfigurationEntry> currentConfigurations, String oldName) {
        List<ConfigurationEntry> entries = currentConfigurations.stream()
                .filter(configurationEntry -> oldName.equals(configurationEntry.getName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(entries)) {
            return null;
        }
        if (entries.size() > 1) {
            LOGGER.debug("More than one configuration with name '{}' was found.", oldName);
        }
        return entries.get(0);
    }

    private List<ConfigurationEntry> getPipelineConfigurations(Pipeline pipeline, String version)
            throws GitClientException {
        String config = gitManager.getConfigFileContent(pipeline, version);
        List<ConfigurationEntry> pipelineConfiguration =
                new PipelineConfigReader().readConfigurations(config, mapper);
        pipelineConfiguration.forEach(entry -> {
            if (entry.getConfiguration() != null) {
                postProcessor.postProcessPipelineConfig(entry.getConfiguration());
                setDockerImageFromPropertiesIfAbsent(entry.getConfiguration());
                setCmdTemplateFromPropertiesIfAbsent(entry.getConfiguration());
                entry.getConfiguration().buildEnvVariables();
            }
        });
        return pipelineConfiguration;
    }

    public String getValidDockerImage(String image) {
        Tool imageTool = toolManager.loadByNameOrId(image);
        return imageTool.getRegistry() + "/" + imageTool.getImage();
    }

    public Revision registerPipelineVersion(final RegisterPipelineVersionVO registerPipelineVersionVO)
            throws GitClientException {
        Pipeline pipeline = pipelineManager.load(registerPipelineVersionVO.getPipelineId());
        return gitManager.createPipelineRevision(pipeline, registerPipelineVersionVO.getVersionName(),
                registerPipelineVersionVO.getCommit(), registerPipelineVersionVO.getMessage(),
                registerPipelineVersionVO.getReleaseDescription());
    }

    private void mergeToolsRequirements(TaskGraphVO result) {
        result.getTasks().forEach(task -> {
            if (task.getTool() != null) {
                Tool toolFromCode = task.getTool();
                Tool tool = toolManager.loadTool(toolFromCode.getRegistry(), toolFromCode.getImage());
                if (!StringUtils.isEmpty(toolFromCode.getCpu())) {
                    tool.setCpu(toolFromCode.getCpu());
                }
                if (!StringUtils.isEmpty(toolFromCode.getRam())) {
                    tool.setRam(toolFromCode.getRam());
                }
                task.setTool(tool);
            }
        });
    }

    private void setCmdTemplateFromPropertiesIfAbsent(PipelineConfiguration pipelineConfiguration) {
        if (pipelineConfiguration != null && pipelineConfiguration.getCmdTemplate() == null) {
            pipelineConfiguration.setCmdTemplate(preferenceManager.getPreference(
                    SystemPreferences.LAUNCH_CMD_TEMPLATE));
        }
    }

    private void setDockerImageFromPropertiesIfAbsent(PipelineConfiguration pipelineConfiguration) {
        String image;
        if (pipelineConfiguration.getDockerImage() != null) {
            image = pipelineConfiguration.getDockerImage();
        } else {
            image = preferenceManager.getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE);
        }
        pipelineConfiguration.setDockerImage(getValidDockerImage(image));
    }

    private List<ConfigurationEntry> removeConfig(String configName,
            List<ConfigurationEntry> currentConfigurations) {
        return currentConfigurations.stream()
                .filter(conf -> conf.getName() == null || !conf.getName().equals(configName))
                .collect(Collectors.toList());
    }

    private ConfigurationEntry getDefaultConfig(List<ConfigurationEntry> currentConfigurations) {
        return currentConfigurations.stream().filter(ConfigurationEntry::getDefaultConfiguration)
                .findAny().orElse(null);
    }

    private PipelineSourceItemVO createConfigVO(List<ConfigurationEntry> currentConfigurations,
            String updatedConfig, String message) {
        String configContent;
        try {
            configContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentConfigurations);
        } catch (JsonProcessingException e) {
            throw new ConfigDecodingException(updatedConfig, e);
        }
        PipelineSourceItemVO source = new PipelineSourceItemVO();
        source.setPath(CONFIG_FILE_NAME);
        source.setContents(configContent);
        source.setComment(message);
        return source;
    }

    private List<ConfigurationEntry> getCurrentConfigurations(Pipeline pipeline)
            throws GitClientException {
        if (pipeline.getCurrentVersion() == null) {
            return Collections.emptyList();
        } else {
            return getPipelineConfigurations(pipeline, pipeline.getCurrentVersion().getName());
        }
    }

    private List<ConfigurationEntry> saveUpdatedConfiguration(String configName, Pipeline pipeline,
            List<ConfigurationEntry> updatedConf, String message) throws GitClientException {
        PipelineSourceItemVO configCommit = createConfigVO(updatedConf, configName, message);
        gitManager.modifyFile(pipeline, configCommit);
        return updatedConf;
    }

    private void checkDefaultConfig(ConfigurationEntry configuration,
            List<ConfigurationEntry> currentConfigurations) {
        if (configuration.getDefaultConfiguration()) {
            currentConfigurations.forEach(config -> {
                if (!configuration.getName().equals(config.getName())) {
                    config.setDefaultConfiguration(false);
                }
            });
        }
    }
}
