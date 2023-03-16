/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.cluster.EnvVarsSettings;
import com.epam.pipeline.entity.cluster.container.ImagePullPolicy;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.utils.CommonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Service
public class PipelineLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineLauncher.class);

    private static final String EMPTY_PARAMETER = "";
    private static final String DEFAULT_CLUSTER_NAME = "CLOUD_PIPELINE";
    private static final String ENV_DELIMITER = ",";
    private static final String CP_POD_PULL_POLICY = "CP_POD_PULL_POLICY";

    @Autowired
    private PipelineExecutor executor;

    @Autowired
    private CommandBuilder commandBuilder;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private UserManager userManager;

    @Value("${kube.namespace}")
    private String kubeNamespace;

    @Value("${launch.script.url.linux}")
    private String linuxLaunchScriptUrl;

    @Value("${launch.script.url.windows}")
    private String windowsLaunchScriptUrl;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private CloudFacade cloudFacade;

    @Autowired
    private MessageHelper messageHelper;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.SIMPLE_DATE_FORMAT);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(Constants.SIMPLE_TIME_FORMAT);

    public String launch(PipelineRun run, PipelineConfiguration configuration, List<String> endpoints,
                         RunAssignPolicy runAssignPolicy, String clusterId) {
        return launch(run, configuration, endpoints, runAssignPolicy, true, run.getPodId(), clusterId);
    }

    public String launch(PipelineRun run, PipelineConfiguration configuration, List<String> endpoints,
                         RunAssignPolicy runAssignPolicy, boolean useLaunch,
                         String pipelineId, String clusterId) {
        return launch(run, configuration, endpoints, runAssignPolicy,
                useLaunch, pipelineId, clusterId, getImagePullPolicy(configuration));
    }

    public String launch(PipelineRun run, PipelineConfiguration configuration,
                         List<String> endpoints, RunAssignPolicy runAssignPolicy,
                         boolean useLaunch, String pipelineId, String clusterId,
                         ImagePullPolicy imagePullPolicy) {
        GitCredentials gitCredentials = configuration.getGitCredentials();
        //TODO: AZURE fix
        Map<SystemParams, String> systemParams = matchSystemParams(
                run,
                preferenceManager.getPreference(SystemPreferences.BASE_API_HOST),
                kubeNamespace,
                preferenceManager.getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING),
                configuration, gitCredentials);
        checkRunOnParentNode(run, runAssignPolicy, systemParams);
        List<EnvVar> envVars = EnvVarsBuilder.buildEnvVars(run, configuration, systemParams,
                buildRegionSpecificEnvVars(run.getInstance().getCloudRegionId(), run.getSensitive(),
                        configuration.getKubeLabels()));

        Assert.isTrue(!StringUtils.isEmpty(configuration.getCmdTemplate()), messageHelper.getMessage(
                MessageConstants.ERROR_CMD_TEMPLATE_NOT_RESOLVED));
        String pipelineCommand = commandBuilder.build(configuration, systemParams);
        String gitCloneUrl = Optional.ofNullable(gitCredentials).map(GitCredentials::getUrl)
                .orElse(run.getRepository());
        String rootPodCommand;
        if (!useLaunch) {
            rootPodCommand = pipelineCommand;
        } else {
            if (KubernetesConstants.WINDOWS.equals(run.getPlatform())) {
                rootPodCommand = String.format(
                        preferenceManager.getPreference(SystemPreferences.LAUNCH_POD_CMD_TEMPLATE_WINDOWS), 
                        windowsLaunchScriptUrl, pipelineCommand);
            } else {
                rootPodCommand = String.format(
                        preferenceManager.getPreference(SystemPreferences.LAUNCH_POD_CMD_TEMPLATE_LINUX), 
                        linuxLaunchScriptUrl, linuxLaunchScriptUrl, gitCloneUrl,
                        run.getRevisionName(), pipelineCommand);
            }
        }
        LOGGER.debug("Start script command: {}", rootPodCommand);
        executor.launchRootPod(rootPodCommand, run, envVars, endpoints, pipelineId,
                runAssignPolicy, configuration.getSecretName(),
                clusterId, imagePullPolicy, configuration.getKubeLabels(),
                configuration.getKubeServiceAccount());
        return pipelineCommand;
    }

    private Map<String, String> buildRegionSpecificEnvVars(final Long cloudRegionId,
                                                           final boolean sensitiveRun,
                                                           final Map<String, String> kubeLabels) {
        final Map<String, String> externalProperties = getExternalProperties(cloudRegionId, sensitiveRun, kubeLabels);
        final Map<String, String> cloudEnvVars = cloudFacade.buildContainerCloudEnvVars(cloudRegionId);
        return CommonUtils.mergeMaps(externalProperties, cloudEnvVars);
    }

    private Map<String, String> getExternalProperties(final Long regionId,
                                                      final boolean sensitiveRun,
                                                      final Map<String, String> kubeLabels) {
        final EnvVarsSettings podEnvVarsFileMap =
                preferenceManager.getPreference(SystemPreferences.LAUNCH_ENV_PROPERTIES);
        if (podEnvVarsFileMap == null) {
            return Collections.emptyMap();
        }
        final Map<String, Object> mergedEnvVars = new HashMap<>(
                MapUtils.emptyIfNull(podEnvVarsFileMap.getDefaultEnvVars()));
        if (sensitiveRun) {
            mergedEnvVars.putAll(MapUtils.emptyIfNull(podEnvVarsFileMap.getSensitiveEnvVars()));
        }
        if (MapUtils.isNotEmpty(kubeLabels)) {
            final Map<String, Map<String, Object>> labelEnvVars =
                    MapUtils.emptyIfNull(podEnvVarsFileMap.getLabelEnvVars());
            kubeLabels.keySet()
                    .forEach(label -> mergedEnvVars.putAll(MapUtils.emptyIfNull(labelEnvVars.get(label))));
        }
        ListUtils.emptyIfNull(podEnvVarsFileMap.getRegionEnvVars())
                .stream()
                .filter(region -> regionId.equals(region.getRegionId()))
                .findFirst()
                .ifPresent(region -> {
                    mergedEnvVars.putAll(MapUtils.emptyIfNull(region.getEnvVars()));
                    if (sensitiveRun) {
                        mergedEnvVars.putAll(MapUtils.emptyIfNull(region.getSensitiveEnvVars()));
                    }
                    if (MapUtils.isNotEmpty(kubeLabels)) {
                        final Map<String, Map<String, Object>> labelEnvVars =
                                MapUtils.emptyIfNull(region.getLabelEnvVars());
                        kubeLabels.keySet()
                                .forEach(label -> mergedEnvVars.putAll(MapUtils.emptyIfNull(labelEnvVars.get(label))));
                    }
                });
        return new ObjectMapper().convertValue(mergedEnvVars, new TypeReference<Map<String, String>>() {});
    }

    private void checkRunOnParentNode(PipelineRun run, RunAssignPolicy assignPolicy,
                                      Map<SystemParams, String> systemParams) {
        if (KubernetesConstants.RUN_ID_LABEL.equals(assignPolicy.getLabel()) &&
                !run.getId().toString().equals(assignPolicy.getValue())) {
            systemParams.put(SystemParams.RUN_ON_PARENT_NODE, EMPTY_PARAMETER);
        }
    }

    public Map<SystemParams, String> matchSystemParams(PipelineRun run, String apiHost, String kubeNamespace,
                                                       boolean enableAutoscaling, PipelineConfiguration configuration,
                                                       GitCredentials gitCredentials) {
        EnumMap<SystemParams, String> systemParamsWithValue = matchCommonParams(run, apiHost, gitCredentials);

        systemParamsWithValue.put(SystemParams.NAMESPACE, kubeNamespace);
        systemParamsWithValue.put(SystemParams.CLUSTER_NAME, DEFAULT_CLUSTER_NAME);
        systemParamsWithValue.put(SystemParams.SSH_PASS, run.getSshPassword());
        putIfStringValuePresent(systemParamsWithValue, SystemParams.RUN_CONFIG_NAME, run.getConfigName());
        if (enableAutoscaling) {
            systemParamsWithValue.put(SystemParams.AUTOSCALING_ENABLED, "");
        }
        if (CollectionUtils.isNotEmpty(run.getRunSids())) {
            Predicate<RunSid> principalFilter = RunSid::getIsPrincipal;
            collectSids(systemParamsWithValue, SystemParams.ALLOWED_USERS,
                    run.getRunSids(), principalFilter);
            collectSids(systemParamsWithValue, SystemParams.ALLOWED_GROUPS,
                    run.getRunSids(), principalFilter.negate());
        }
        systemParamsWithValue.put(SystemParams.CONTAINER_CPU_RESOURCE, String.valueOf(
                preferenceManager.getPreference(SystemPreferences.LAUNCH_CONTAINER_CPU_RESOURCE)));
        String securedEnvVars = Arrays
                .stream(SystemParams.values())
                .filter(SystemParams::isSecure)
                .map(SystemParams::getEnvName)
                .collect(Collectors.joining(ENV_DELIMITER));

        systemParamsWithValue.put(SystemParams.SECURE_ENV_VARS, securedEnvVars);
        if (!MapUtils.emptyIfNull(configuration.getEnvironmentParams())
                .containsKey(SystemParams.RESUMED_RUN.getEnvName())) {
            systemParamsWithValue.put(SystemParams.RESUMED_RUN, "false");
        }
        if (run.getSensitive()) {
            systemParamsWithValue.put(SystemParams.CP_SENSITIVE_RUN, "true");
        }
        if (run.getTimeout() != null && run.getTimeout() > 0) {
            systemParamsWithValue.put(SystemParams.CP_EXEC_TIMEOUT, String.valueOf(run.getTimeout()));
        }
        return systemParamsWithValue;
    }

    public EnumMap<SystemParams, String> matchCommonParams(PipelineRun run,
                                                           String apiHost,
                                                           GitCredentials gitCredentials) {
        EnumMap<SystemParams, String> systemParamsWithValue = new EnumMap<>(SystemParams.class);

        if (run.getPipelineId() == null || run.getVersion() == null) {
            systemParamsWithValue.put(SystemParams.PIPELINE_VERSION, EMPTY_PARAMETER);
            systemParamsWithValue.put(SystemParams.PIPELINE_ID, EMPTY_PARAMETER);
        } else {
            systemParamsWithValue.put(SystemParams.PIPELINE_VERSION, run.getVersion());
            systemParamsWithValue.put(SystemParams.PIPELINE_ID, String.valueOf(run.getPipelineId()));
        }

        systemParamsWithValue.put(SystemParams.API, apiHost);
        systemParamsWithValue.put(SystemParams.API_EXTERNAL, preferenceManager.getPreference(
                SystemPreferences.BASE_API_HOST_EXTERNAL));
        systemParamsWithValue.put(SystemParams.DISTRIBUTION_URL, preferenceManager.getPreference(
                SystemPreferences.BASE_PIPE_DISTR_URL));
        systemParamsWithValue.put(SystemParams.PARENT, run.getPodId());
        systemParamsWithValue.put(SystemParams.PIPELINE_NAME,
                Optional.ofNullable(run.getPipelineName())
                        .orElse(PipelineRun.DEFAULT_PIPELINE_NAME)
                        .replaceAll("\\s+", ""));
        systemParamsWithValue
                .put(SystemParams.RUN_DATE, dateFormat.format(run.getStartDate()));
        systemParamsWithValue
                .put(SystemParams.RUN_TIME, timeFormat.format(run.getStartDate()));
        systemParamsWithValue.put(SystemParams.RUN_ID, run.getId().toString());

        UserContext owner = Optional.ofNullable(authManager.getUserContext())
                .orElse(userManager.loadUserContext(run.getOwner()));
        systemParamsWithValue.put(SystemParams.API_TOKEN, authManager
                .issueToken(owner, null).getToken());
        systemParamsWithValue.put(SystemParams.OWNER, run.getOwner());
        if (gitCredentials != null) {
            putIfStringValuePresent(systemParamsWithValue,
                    SystemParams.GIT_USER, gitCredentials.getUserName());
            putIfStringValuePresent(systemParamsWithValue,
                    SystemParams.GIT_TOKEN, gitCredentials.getToken());
        }
        if (run.getParentRunId() != null) {
            systemParamsWithValue.put(SystemParams.PARENT_ID, String.valueOf(run.getParentRunId()));
        }

        systemParamsWithValue.put(SystemParams.FSBROWSER_ENABLED,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_ENABLED).getValue());
        putIfStringValuePresent(systemParamsWithValue,
                SystemParams.FSBROWSER_PORT,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_PORT).getValue());
        putIfStringValuePresent(systemParamsWithValue,
                SystemParams.FSBROWSER_WD,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_WD).getValue());
        putIfStringValuePresent(systemParamsWithValue,
                SystemParams.FSBROWSER_TMP,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_TMP).getValue());
        putIfStringValuePresent(systemParamsWithValue,
                SystemParams.FSBROWSER_STORAGE,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_TRANSFER).getValue());
        putIfStringValuePresent(systemParamsWithValue,
                SystemParams.FSBROWSER_BLACK_LIST,
                preferenceManager.getSystemPreference(SystemPreferences.STORAGE_FSBROWSER_BLACK_LIST).getValue());
        return systemParamsWithValue;
    }

    private void collectSids(EnumMap<SystemParams, String> params, SystemParams parameter,
                             List<RunSid> sids, Predicate<RunSid> filter) {
        String filtered = sids.stream()
                .filter(filter::test)
                .map(RunSid::getName)
                .collect(Collectors.joining(ENV_DELIMITER));
        params.put(parameter, filtered);

    }

    private void putIfStringValuePresent(EnumMap<SystemParams, String> params,
                                         SystemParams parameter,
                                         String value) {
        if (StringUtils.hasText(value)) {
            params.put(parameter, value);
        }
    }

    private ImagePullPolicy getImagePullPolicy(final PipelineConfiguration configuration) {
        final PipeConfValueVO value = MapUtils.emptyIfNull(configuration.getParameters())
                .get(CP_POD_PULL_POLICY);
        if (value == null) {
            return ImagePullPolicy.ALWAYS;
        }
        return ImagePullPolicy.getByNameOrDefault(value.getValue());
    }
}
