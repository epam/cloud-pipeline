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
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PipelineConfigurationManager {

    public static final String MASTER_CLUSTER_ROLE = "master";
    public static final String NFS_CLUSTER_ROLE = "CP_CAP_NFS";
    public static final String ERASE_RUN_ENDPOINTS = "CP_DISABLE_RUN_ENDPOINTS";
    public static final String ERASE_WORKER_ENDPOINTS = "CP_DISABLE_WORKER_ENDPOINTS";
    public static final String GE_AUTOSCALING = "CP_CAP_AUTOSCALE";
    public static final String WORKER_CLUSTER_ROLE = "worker";
    public static final String WORKER_CMD_TEMPLATE = "sleep infinity";

    @Autowired
    private PipelineVersionManager pipelineVersionManager;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private DataStorageApiService dataStorageApiService;

    @Autowired
    private PermissionsService permissionsService;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    private Function<AbstractDataStorage, String> mountOptionsSupplier = (ds) -> {
        if (ds instanceof NFSDataStorage) {
            String mountOptions = ds.getMountOptions();
            if (!permissionsService.isMaskBitSet(ds.getMask(), ((AclPermission)AclPermission.WRITE).getSimpleMask())) {
                mountOptions += ",ro";
            }
            return mountOptions;
        } else {
            return "";
        }
    };

    public PipelineConfiguration getPipelineConfiguration(final PipelineStart runVO) {
        return getPipelineConfiguration(runVO, null);
    }

    public PipelineConfiguration getPipelineConfiguration(final PipelineStart runVO, final Tool tool) {
        PipelineConfiguration configuration;
        PipelineConfiguration defaultConfiguration = new PipelineConfiguration();

        boolean toolRun = tool != null;
        boolean pipelineRun = runVO.getPipelineId() != null;

        if (toolRun || pipelineRun) {
            defaultConfiguration = getConfigurationForToolRunOrPipelineRun(runVO, tool, toolRun);
        }

        configuration = mergeParameters(runVO, defaultConfiguration);
        if (pipelineRun) {
            configuration.setGitCredentials(gitManager.getGitCredentials(runVO.getPipelineId()));
        }
        if (toolRun) {
            mergeParametersFromTool(configuration, tool);
        }

        List<AbstractDataStorage> dataStorages = dataStorageApiService.getWritableStorages();
        configuration.setBuckets(zipToString(dataStorages, AbstractDataStorage::getPathMask));
        configuration.setNfsMountOptions(zipToString(dataStorages, mountOptionsSupplier));
        configuration.setMountPoints(zipToString(dataStorages, AbstractDataStorage::getMountPoint));
        //client always sends actual node count value
        configuration.setNodeCount(Optional.ofNullable(runVO.getNodeCount()).orElse(0));
        configuration.setCloudRegionId(runVO.getCloudRegionId());
        setEndpointsErasure(configuration);
        return configuration;
    }

    public PipelineConfiguration mergeParameters(PipelineStart runVO, PipelineConfiguration defaultConfig) {
        Map<String, PipeConfValueVO> params = runVO.getParams() == null
                ? Collections.emptyMap() : runVO.getParams();
        PipelineConfiguration configuration = new PipelineConfiguration();

        configuration.setMainFile(defaultConfig.getMainFile());
        configuration.setMainClass(defaultConfig.getMainClass());
        configuration.setEnvironmentParams(defaultConfig.getEnvironmentParams());
        configuration.setPrettyUrl(runVO.getPrettyUrl());
        configuration.setCloudRegionId(defaultConfig.getCloudRegionId());
        Map<String, PipeConfValueVO> runParameters = new LinkedHashMap<>();

        //filter and get parameters from user
        params.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isRequired() || !StringUtils.isEmpty(entry.getValue().getValue()))
                .forEach(entry -> runParameters.put(entry.getKey(), entry.getValue()));

        //fill in default values, only if user's value wasn't provided
        if (defaultConfig.getParameters() != null) {
            defaultConfig.getParameters()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().isRequired() || !StringUtils.isEmpty(entry.getValue().getValue()))
                    .forEach(entry -> runParameters.putIfAbsent(entry.getKey(), entry.getValue()));
        }

        //set parameters to the final configuration
        configuration.setParameters(runParameters);

        //fill instance settings
        if (runVO.getHddSize() != null) {
            configuration.setInstanceDisk(String.valueOf(runVO.getHddSize()));
        } else {
            configuration.setInstanceDisk(defaultConfig.getInstanceDisk());
        }
        if (runVO.getInstanceType() != null) {
            configuration.setInstanceType(String.valueOf(runVO.getInstanceType()));
        } else {
            configuration.setInstanceType(defaultConfig.getInstanceType());
        }
        if (runVO.getTimeout() != null) {
            configuration.setTimeout(runVO.getTimeout());
        } else {
            configuration.setTimeout(defaultConfig.getTimeout());
        }

        //client always sends actual node-count
        configuration.setNodeCount(runVO.getNodeCount());

        configuration.setCmdTemplate(
                runVO.getCmdTemplate() == null ? defaultConfig.getCmdTemplate() : runVO.getCmdTemplate()
        );
        Boolean isSpot = runVO.getIsSpot() == null ? defaultConfig.getIsSpot() : runVO.getIsSpot();
        configuration.setIsSpot(isSpot);

        if (isSpot != null && !isSpot) {
            configuration.setNonPause(runVO.isNonPause());
        }

        configuration.setWorkerCmd(
                runVO.getWorkerCmd() == null ? defaultConfig.getWorkerCmd() : runVO.getWorkerCmd());
        configuration.setDockerImage(chooseDockerImage(runVO, defaultConfig));
        configuration.buildEnvVariables();
        return configuration;
    }

    /**
     * Modifies configuration if this a cluster run configuration.
     *
     * @param configuration Configuration to be modified.
     * @param isNFS Specifies if NFS should be enabled for cluster configuration.
     * @return True if this is a cluster configuration.
     */
    public boolean initClusterConfiguration(PipelineConfiguration configuration, boolean isNFS) {
        if ((configuration.getNodeCount() == null || configuration.getNodeCount() <= 0)
                && !hasBooleanParameter(configuration, GE_AUTOSCALING)) {
            return false;
        }
        updateMasterConfiguration(configuration, isNFS);
        return true;
    }

    public void updateMasterConfiguration(PipelineConfiguration configuration, boolean isNFS) {
        configuration.setClusterRole(MASTER_CLUSTER_ROLE);
        if (isNFS) {
            configuration.getParameters().put(NFS_CLUSTER_ROLE, new PipeConfValueVO("true"));
        }
        configuration.buildEnvVariables();
    }

    public void updateWorkerConfiguration(String parentId, PipelineStart runVO,
            PipelineConfiguration configuration, boolean isNFS, boolean clearParams) {
        configuration.setEraseRunEndpoints(hasBooleanParameter(configuration, ERASE_WORKER_ENDPOINTS));
        final Map<String, PipeConfValueVO> configParameters = MapUtils.isEmpty(configuration.getParameters()) ?
                new HashMap<>() : configuration.getParameters();
        final Map<String, PipeConfValueVO> updatedParams = clearParams ? new HashMap<>() : configParameters;
        final List<DefaultSystemParameter> systemParameters = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_SYSTEM_PARAMETERS);
        ListUtils.emptyIfNull(systemParameters)
                .stream()
                .filter(param -> param.isPassToWorkers() &&
                        configParameters.containsKey(param.getName()))
                .forEach(param -> {
                    final String paramName = param.getName();
                    updatedParams.put(paramName, configParameters.get(paramName));
                });

        updatedParams.put(PipelineRun.PARENT_ID_PARAM, new PipeConfValueVO(parentId));
        if (isNFS) {
            updatedParams.put(NFS_CLUSTER_ROLE, new PipeConfValueVO("true"));
        } else {
            updatedParams.remove(NFS_CLUSTER_ROLE);
        }
        configuration.setParameters(updatedParams);
        configuration.setClusterRole(WORKER_CLUSTER_ROLE);
        configuration.setCmdTemplate(StringUtils.hasText(runVO.getWorkerCmd()) ?
                runVO.getWorkerCmd() : WORKER_CMD_TEMPLATE);
        configuration.setPrettyUrl(null);
        //remove node count parameter for workers
        configuration.setNodeCount(null);
        configuration.buildEnvVariables();
    }

    public boolean hasNFSParameter(PipelineConfiguration entry) {
        return hasBooleanParameter(entry, NFS_CLUSTER_ROLE);
    }

    private boolean hasBooleanParameter(PipelineConfiguration entry, String parameterName) {
        return entry.getParameters().entrySet().stream().anyMatch(e ->
                e.getKey().equals(parameterName) && e.getValue().getValue() != null
                        && e.getValue().getValue().equalsIgnoreCase("true"));
    }

    private void setEndpointsErasure(PipelineConfiguration configuration) {
        if (MapUtils.isEmpty(configuration.getParameters())) {
            return;
        }
        configuration.setEraseRunEndpoints(hasBooleanParameter(configuration, ERASE_RUN_ENDPOINTS));
    }

    public PipelineConfiguration getConfigurationFromRun(PipelineRun run) {
        PipelineConfiguration configuration = new PipelineConfiguration();

        configuration.setParameters(run.convertParamsToMap());
        configuration.setTimeout(run.getTimeout());
        configuration.setNodeCount(run.getNodeCount());

        configuration.setCmdTemplate(run.getCmdTemplate());
        configuration.setDockerImage(run.getDockerImage());

        RunInstance instance = run.getInstance();
        if (instance != null) {
            configuration.setInstanceDisk(String.valueOf(instance.getEffectiveNodeDisk()));
            configuration.setInstanceType(instance.getNodeType());
            configuration.setIsSpot(instance.getSpot());
            configuration.setInstanceImage(instance.getNodeImage());
            configuration.setCloudRegionId(instance.getCloudRegionId());
        }

        setEndpointsErasure(configuration);
        if (run.getPipelineId() != null) {
            try {
                ConfigurationEntry entry = pipelineVersionManager
                        .loadConfigurationEntry(run.getPipelineId(), run.getVersion(), run.getConfigName());
                PipelineConfiguration defaultConfiguration = entry.getConfiguration();
                configuration.setEnvironmentParams(defaultConfiguration.getEnvironmentParams());
                configuration.setMainClass(defaultConfiguration.getMainClass());
                configuration.setMainFile(defaultConfiguration.getMainFile());
            } catch (GitClientException e) {
                log.error(e.getMessage(), e);
            }
            configuration.setGitCredentials(gitManager.getGitCredentials(run.getPipelineId()));
        }
        configuration.buildEnvVariables();
        return configuration;
    }

    private String zipToString(List<AbstractDataStorage> dataStorages,
                               Function<AbstractDataStorage, String> fieldValueSupplier) {
        return dataStorages.stream()
            .map(fieldValueSupplier)
            .collect(Collectors.joining(";"));
    }

    private String chooseDockerImage(PipelineStart runVO, PipelineConfiguration defaultConfig) {
        if (runVO.getDockerImage() != null) {
            return pipelineVersionManager.getValidDockerImage(runVO.getDockerImage());
        }
        return defaultConfig.getDockerImage();
    }

    private void mergeParametersFromTool(final PipelineConfiguration configuration, final Tool tool) {
        if (configuration.getInstanceType() == null) {
            configuration.setInstanceType(tool.getInstanceType());
        }
        if (configuration.getInstanceDisk() == null) {
            configuration.setInstanceDisk(String.valueOf(tool.getDisk()));
        }
    }

    private ConfigurationEntry initRegisteredPipelineConfiguration(final Long pipelineId, final String version,
                                                                   final String configurationName) {
        try {
            return pipelineVersionManager.loadConfigurationEntry(pipelineId, version, configurationName);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private ConfigurationEntry getConfigurationForToolVersion(final Long toolId, final String dockerImage,
                                                              final String configurationName) {
        String tag = toolManager.getTagFromImageName(dockerImage);
        List<ConfigurationEntry> configurationEntries =
                toolVersionManager.loadToolVersionSettings(toolId, tag)
                        .stream()
                        .findFirst()
                        .map(ToolVersion::getSettings)
                        .orElse(Collections.emptyList());
        if (CollectionUtils.isEmpty(configurationEntries)) {
            return null;
        }
        if (StringUtils.hasText(configurationName)) {
            return configurationEntries
                    .stream()
                    .filter(configurationEntry -> configurationEntry.getName() != null
                            && configurationEntry.getName().equals(configurationName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(messageHelper
                            .getMessage(MessageConstants.ERROR_CONFIG_NOT_FOUND, configurationName)));
        } else {
            return configurationEntries
                    .stream()
                    .filter(ConfigurationEntry::getDefaultConfiguration)
                    .findAny()
                    .orElse(configurationEntries.get(0));
        }
    }

    private PipelineConfiguration getConfigurationForToolRunOrPipelineRun(final PipelineStart runVO, final Tool tool,
                                                                          final boolean toolRun) {
        PipelineConfiguration defaultConfiguration;
        ConfigurationEntry entry;
        if (toolRun) {
            entry = getConfigurationForToolVersion(tool.getId(), runVO.getDockerImage(),
                    runVO.getConfigurationName());
        } else {
            entry = initRegisteredPipelineConfiguration(runVO.getPipelineId(), runVO.getVersion(),
                    runVO.getConfigurationName());
        }
        defaultConfiguration = entry == null ? new PipelineConfiguration() : entry.getConfiguration();
        if (!StringUtils.hasText(runVO.getConfigurationName()) && entry != null) {
            runVO.setConfigurationName(entry.getName());
        }
        return defaultConfiguration;
    }

    public PipelineConfiguration getConfigurationForTool(final Tool tool, final PipelineConfiguration configuration) {
        return Optional.ofNullable(getConfigurationForToolVersion(tool.getId(), configuration.getDockerImage(), null))
                .map(ConfigurationEntry::getConfiguration)
                .orElseGet(PipelineConfiguration::new);
    }
}
