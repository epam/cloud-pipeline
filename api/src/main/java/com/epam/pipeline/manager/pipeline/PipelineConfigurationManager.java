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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.utils.RegExpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

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
    public static final String INHERITABLE_PARAMETER_NAMES = "CP_CAP_AUTOSCALE_INHERITABLE_PARAMETER_NAMES";
    public static final String INHERITABLE_PARAMETER_PREFIXES = "CP_CAP_AUTOSCALE_INHERITABLE_PARAMETER_PREFIXES";

    @Autowired
    private PipelineVersionManager pipelineVersionManager;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionManager regionManager;

    @Autowired
    private PipelineConfigurationLaunchCapabilitiesProcessor launchCapabilitiesProcessor;

    public PipelineConfiguration getPipelineConfigurationForPipeline(final Pipeline pipeline,
                                                                     final PipelineStart runVO) {
        if (PipelineType.VERSIONED_STORAGE.equals(pipeline.getPipelineType())) {
            Assert.isTrue(StringUtils.hasText(runVO.getDockerImage()),
                    messageHelper.getMessage(MessageConstants.ERROR_IMAGE_NOT_FOUND_FOR_VERSIONED_STORAGE));
            return getPipelineConfiguration(runVO, toolManager.loadByNameOrId(runVO.getDockerImage()));
        }
        return getPipelineConfiguration(runVO, null);
    }

    public PipelineConfiguration getPipelineConfiguration(final PipelineStart runVO) {
        final Long pipelineId = runVO.getPipelineId();
        if (Objects.nonNull(pipelineId)) {
            final Pipeline pipeline = pipelineManager.load(pipelineId);
            return getPipelineConfigurationForPipeline(pipeline, runVO);
        }
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

        getParametersFromNetworkConfig(configuration.getInstanceType(), runVO.getCloudRegionId())
                .forEach((key, parameter) -> {
                    if(!configuration.getParameters().containsKey(key)) {
                        configuration.getParameters().put(key, parameter);
                    }
                });

        //client always sends actual node count value
        configuration.setNodeCount(Optional.ofNullable(runVO.getNodeCount()).orElse(0));
        configuration.setCloudRegionId(runVO.getCloudRegionId());
        setEndpointsErasure(configuration);
        return configuration;
    }

    public PipelineConfiguration mergeParameters(PipelineStart runVO, PipelineConfiguration defaultConfig) {
        Map<String, PipeConfValueVO> params = Optional.ofNullable(runVO.getParams()).orElseGet(Collections::emptyMap);
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

        runParameters.putAll(launchCapabilitiesProcessor.process(runParameters));

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
            configuration.setInstanceType(runVO.getInstanceType());
        } else {
            configuration.setInstanceType(defaultConfig.getInstanceType());
        }
        if (runVO.getTimeout() != null) {
            configuration.setTimeout(runVO.getTimeout());
        } else {
            configuration.setTimeout(defaultConfig.getTimeout());
        }
        if (runVO.getInstanceImage() != null) {
            configuration.setInstanceImage(runVO.getInstanceImage());
        } else {
            configuration.setInstanceImage(defaultConfig.getInstanceImage());
        }
        if (runVO.getNotifications() != null) {
            configuration.setNotifications(runVO.getNotifications());
        } else {
            configuration.setNotifications(defaultConfig.getNotifications());
        }

        if (MapUtils.isNotEmpty(runVO.getKubeLabels())) {
            configuration.setKubeLabels(runVO.getKubeLabels());
        } else {
            configuration.setKubeLabels(defaultConfig.getKubeLabels());
        }

        // TODO: merging parentNodeId together with runAssignPolicy,
        //  in a future we can delete it if we get rid of parentNodeId in favor of runAssignPolicy
        configuration.setPodAssignPolicy(mergeAssignPolicy(runVO, defaultConfig));

        if (!StringUtils.isEmpty(runVO.getKubeServiceAccount())) {
            configuration.setKubeServiceAccount(runVO.getKubeServiceAccount());
        } else {
            configuration.setKubeServiceAccount(defaultConfig.getKubeServiceAccount());
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
        configuration.setRunAs(mergeRunAs(runVO, defaultConfig));
        configuration.setSharedWithUsers(defaultConfig.getSharedWithUsers());
        configuration.setSharedWithRoles(defaultConfig.getSharedWithRoles());
        configuration.setTags(runVO.getTags());
        return configuration;
    }

    private RunAssignPolicy mergeAssignPolicy(final PipelineStart runVO, final PipelineConfiguration defaultConfig) {
        final Long useRunId = runVO.getParentNodeId() != null ? runVO.getParentNodeId() : runVO.getUseRunId();
        final RunAssignPolicy assignPolicy = runVO.getPodAssignPolicy();

        if (useRunId != null && assignPolicy != null) {
            throw new IllegalArgumentException(
                    "Both RunAssignPolicy and (parentRunId or useRunId) cannot be specified, " +
                    "please provide only one of them"
            );
        }

        if (assignPolicy != null && assignPolicy.isValid()) {
            log.debug("RunAssignPolicy is provided and valid, will proceed with it.");
            return assignPolicy;
        } else {
            if (useRunId != null) {
                final String value = useRunId.toString();
                log.debug(
                    String.format("Configuring RunAssignPolicy as: label %s, value: %s.",
                            KubernetesConstants.RUN_ID_LABEL, value)
                );
                return RunAssignPolicy.builder()
                        .selector(
                            RunAssignPolicy.PodAssignSelector.builder()
                                .label(KubernetesConstants.RUN_ID_LABEL)
                                .value(value).build())
                        .build();
            } else {
                if (defaultConfig.getPodAssignPolicy() != null && defaultConfig.getPodAssignPolicy().isValid()) {
                    return defaultConfig.getPodAssignPolicy();
                }
                return RunAssignPolicy.builder().build();
            }
        }
    }

    /**
     * Modifies configuration if this a cluster run configuration.
     *
     * @param configuration Configuration to be modified.
     * @param isNFS Specifies if NFS should be enabled for cluster configuration.
     * @return True if this is a cluster configuration.
     */
    public boolean initClusterConfiguration(PipelineConfiguration configuration, boolean isNFS) {
        if (!isClusterConfiguration(configuration)) {
            return false;
        }
        updateMasterConfiguration(configuration, isNFS);
        return true;
    }

    public static boolean isClusterConfiguration(final PipelineConfiguration configuration) {
        return !(configuration.getNodeCount() == null || configuration.getNodeCount() <= 0)
                || hasBooleanParameter(configuration, GE_AUTOSCALING);
    }

    public void updateMasterConfiguration(PipelineConfiguration configuration, boolean isNFS) {
        configuration.setClusterRole(MASTER_CLUSTER_ROLE);
        if (isNFS) {
            configuration.getParameters().put(NFS_CLUSTER_ROLE, new PipeConfValueVO("true"));
        }
        configuration.buildEnvVariables();
    }

    public PipelineConfiguration generateMasterConfiguration(final PipelineConfiguration configuration,
                                                             final boolean isNFS) {
        final PipelineConfiguration copiedConfiguration = configuration.clone();
        updateMasterConfiguration(copiedConfiguration, isNFS);
        return copiedConfiguration;
    }

    public PipelineConfiguration generateWorkerConfiguration(final String parentId, final PipelineStart runVO,
                                                             final PipelineConfiguration configuration,
                                                             final boolean isNFS, final boolean clearParams) {
        final PipelineConfiguration workerConfiguration = configuration.clone();
        workerConfiguration.setEraseRunEndpoints(hasBooleanParameter(workerConfiguration, ERASE_WORKER_ENDPOINTS));
        final Map<String, PipeConfValueVO> configParameters = MapUtils.isEmpty(workerConfiguration.getParameters()) ?
                new HashMap<>() : workerConfiguration.getParameters();
        final Map<String, PipeConfValueVO> updatedParams = clearParams ? new HashMap<>() : configParameters;
        final List<DefaultSystemParameter> systemParameters = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_SYSTEM_PARAMETERS);
        ListUtils.emptyIfNull(systemParameters)
                .stream()
                .filter(DefaultSystemParameter::isPassToWorkers)
                .forEach(param -> {
                    if (param.isPrefix()) {
                        processPrefixParam(param.getName(), configParameters, updatedParams);
                    } else {
                        processInheritedParam(param.getName(), configParameters, updatedParams);
                    }
                });

        processExplicitlyInheritedParams(INHERITABLE_PARAMETER_NAMES, configParameters, updatedParams, false);
        processExplicitlyInheritedParams(INHERITABLE_PARAMETER_PREFIXES, configParameters, updatedParams, true);

        updatedParams.put(PipelineRun.PARENT_ID_PARAM, new PipeConfValueVO(parentId));
        if (isNFS) {
            updatedParams.put(NFS_CLUSTER_ROLE, new PipeConfValueVO("true"));
        } else {
            updatedParams.remove(NFS_CLUSTER_ROLE);
        }
        workerConfiguration.setParameters(updatedParams);
        workerConfiguration.setClusterRole(WORKER_CLUSTER_ROLE);
        workerConfiguration.setCmdTemplate(StringUtils.hasText(runVO.getWorkerCmd()) ?
                runVO.getWorkerCmd() : WORKER_CMD_TEMPLATE);
        workerConfiguration.setPrettyUrl(null);
        //remove node count parameter for workers
        workerConfiguration.setNodeCount(null);
        // if podAssignPolicy is a simple policy to assign run pod to dedicated instance, then we need to cleared it
        // and workers then will be assigned to its own nodes, otherwise keep existing policy to assign workers
        // as was configured in policy object
        if (workerConfiguration.getPodAssignPolicy().isMatch(KubernetesConstants.RUN_ID_LABEL, parentId)) {
            workerConfiguration.setPodAssignPolicy(null);
        }
        workerConfiguration.buildEnvVariables();
        return workerConfiguration;
    }

    public boolean hasNFSParameter(PipelineConfiguration entry) {
        return hasBooleanParameter(entry, NFS_CLUSTER_ROLE);
    }

    private static boolean hasBooleanParameter(PipelineConfiguration entry, String parameterName) {
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

    private Map<String, PipeConfValueVO> getParametersFromNetworkConfig(final String instanceType,
                                                                        final Long cloudRegionId) {
        final Map<String, PipeConfValueVO> parameters = new HashMap<>();
        final AbstractCloudRegion runRegion = regionManager.loadOrDefault(cloudRegionId);
        final CloudRegionsConfiguration cloudRegionsConfiguration = preferenceManager.getPreference(
                SystemPreferences.CLUSTER_NETWORKS_CONFIG);

        if (cloudRegionsConfiguration == null || !StringUtils.hasText(instanceType)) {
            return Collections.emptyMap();
        }

        ListUtils.emptyIfNull(cloudRegionsConfiguration.getRegions())
                .stream()
                .filter(r -> runRegion.getRegionCode().equals(r.getName()) || runRegion.getId().equals(r.getRegionId()))
                .flatMap(networkConfiguration -> ListUtils.emptyIfNull(networkConfiguration.getAmis()).stream())
                .filter(
                        ami -> {
                            final String instanceRegExp = RegExpUtils.getRegExpFormGlob(ami.getInstanceMask());
                            return Pattern.compile(instanceRegExp).matcher(instanceType).find();
                        }
                ).findFirst()
                .map(AMIConfiguration::getRunParameters)
                .ifPresent(p -> p.forEach((k, v) -> parameters.put(k, buildPipeConfValue(v))));
        return parameters;
    }

    private static PipeConfValueVO buildPipeConfValue(Object value) {
        String type = PipeConfValueVO.DEFAULT_TYPE;
        if (value instanceof Integer || value instanceof Long) {
            type = "int";
        } else if (value instanceof Boolean) {
            type = "boolean";
        }
        return new PipeConfValueVO(value.toString(), type);
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

    private String mergeRunAs(final PipelineStart runVO, final PipelineConfiguration configuration) {
        return StringUtils.isEmpty(configuration.getRunAs()) ? runVO.getRunAs() : configuration.getRunAs();
    }

    private static void processExplicitlyInheritedParams(final String paramName,
                                                         final Map<String, PipeConfValueVO> configParameters,
                                                         final Map<String, PipeConfValueVO> updatedParams,
                                                         final boolean prefix) {
        final PipeConfValueVO inheritableParameters = configParameters.get(paramName);
        if (inheritableParameters != null && StringUtils.hasText(inheritableParameters.getValue())) {
            Arrays.stream(StringUtils.commaDelimitedListToStringArray(inheritableParameters.getValue()))
                    .forEach(param -> {
                        if (prefix) {
                            processPrefixParam(param, configParameters, updatedParams);
                        } else {
                            processInheritedParam(param, configParameters, updatedParams);
                        }
                    });
        }
    }

    private static void processInheritedParam(final String paramName,
                                              final Map<String, PipeConfValueVO> configParameters,
                                              final Map<String, PipeConfValueVO> updatedParams) {
        if (configParameters.containsKey(paramName)) {
            updatedParams.put(paramName, configParameters.get(paramName));
        }
    }

    private static void processPrefixParam(final String paramName,
                                           final Map<String, PipeConfValueVO> configParameters,
                                           final Map<String, PipeConfValueVO> updatedParams) {
        configParameters.forEach((name, value) -> {
            if (name.startsWith(paramName)) {
                updatedParams.put(name, value);
            }
        });
    }
}
