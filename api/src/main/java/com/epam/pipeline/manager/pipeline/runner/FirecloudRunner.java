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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.FirecloudPreferences;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.google.CredentialsManager;
import com.epam.pipeline.manager.google.FirecloudCredentials;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.ParameterMapper;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.PasswordGenerator;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Launches execution of {@link com.epam.pipeline.entity.configuration.RunConfigurationEntry}
 * in Firecloud environment. A wrapper tool defined in application preferences is used
 * to run analysis in Firecloud and transfer data to and from Google Cloud Storage
 */
@Service
@RequiredArgsConstructor
public class FirecloudRunner implements ExecutionRunner<FirecloudRunConfigurationEntry> {

    private final FolderManager folderManager;
    private final ParameterMapper parameterMapper;
    private final PreferenceManager preferenceManager;
    private final CredentialsManager credentialsManager;
    private final PipelineConfigurationManager pipelineConfigurationManager;
    private final PipelineRunManager pipelineRunManager;
    private final MetadataEntityManager metadataEntityManager;

    @Override
    public List<PipelineRun> runAnalysis(AnalysisConfiguration<FirecloudRunConfigurationEntry> configuration) {
        Assert.state(CollectionUtils.isNotEmpty(configuration.getEntitiesIds()),
                "Entities are required to run analysis in Firecloud");
        FolderWithMetadata project = folderManager.getProject(configuration.getConfigurationId(),
                AclClass.CONFIGURATION);
        Assert.notNull(project, "Project folder must be specified to run analysis in Firecloud.");
        List<Long> entities = parameterMapper.fetchAndExpandInputEntities(configuration).values()
                .stream()
                .flatMap(Collection::stream)
                .map(BaseEntity::getId)
                .collect(Collectors.toList());
        //TODO: after merge - add check that FC class is filled
        configuration.getEntries().forEach(this::validateFirecloudConfiguration);
        FirecloudConfiguration settings = buildFirecloudConfig(configuration.getRefreshToken());
        return configuration.getEntries().stream()
                .map(entry -> runFirecloudAnalysis(settings, entry,
                        configuration.getConfigurationId(), entities, project.getId()))
                .collect(Collectors.toList());
    }

    private FirecloudConfiguration buildFirecloudConfig(String refreshToken) {
        FirecloudCredentials credentials = credentialsManager.getFirecloudCredentials(refreshToken);
        return FirecloudConfiguration.builder()
                .firecloudLauncherImage(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_LAUNCHER_TOOL))
                .firecloudLauncherCmd(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_LAUNCHER_CMD))
                .billingProject(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_BILLING_PROJECT))
                .instanceType(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_INSTANCE_TYPE))
                .instanceDisk(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_INSTANCE_DISK))
                .authClientId(credentials.getClientId())
                .authClientSecret(credentials.getClientSecret())
                .refreshToken(credentials.getRefreshToken())
                .build();
    }

    private void validateFirecloudConfiguration(FirecloudRunConfigurationEntry firecloudEntry) {
        Assert.state(firecloudEntry.checkConfigComplete(),
                "Method or method configuration is missing");
        if (shouldCreateNewMethodConfig(firecloudEntry)) {
            validateRootEntityType(firecloudEntry.getRootEntityId());
        } else {
            Assert.isTrue(StringUtils.isNotBlank(firecloudEntry.getMethodConfigurationSnapshot()),
                    "Method configuration snapshot is required.");
        }
    }

    private void validateRootEntityType(Long rootEntityClassId) {
        Assert.notNull(rootEntityClassId, "Root entity class ID is required for creating new configuration.");
        MetadataClass rootEntityClass = metadataEntityManager.loadClass(rootEntityClassId);
        Assert.notNull(rootEntityClass.getFireCloudClassName(),
                String.format("Firecloud type must be provider for entity type %s.", rootEntityClass.getName()));
    }

    private PipelineRun runFirecloudAnalysis(FirecloudConfiguration settings,
                                             FirecloudRunConfigurationEntry entry,
                                             Long configurationId,
                                             List<Long> entities, Long projectId) {
        FireCloudClass rootEntityType = metadataEntityManager.loadClass(entry.getRootEntityId())
                .getFireCloudClassName();
        PipelineStart startVO = createFirecloudStart(settings, entry, entities, projectId, configurationId,
                rootEntityType);
        PipelineConfiguration runConfiguration =
                pipelineConfigurationManager.getPipelineConfiguration(startVO);
        runConfiguration.setExecutionPreferences(FirecloudPreferences.builder()
                .method(entry.getMethodName())
                .methodSnapshot(entry.getMethodSnapshot())
                .methodConfiguration(entry.getMethodConfigurationName())
                .methodConfigurationSnapshot(entry.getMethodSnapshot())
                .build());
        addCredentials(runConfiguration, settings);
        return pipelineRunManager.launchPipeline(runConfiguration, null, null,
                startVO.getInstanceType(), startVO.getParentNodeId(),
                startVO.getConfigurationName(), null, null, entities, configurationId,
                startVO.getRunSids());
    }

    private PipelineStart createFirecloudStart(FirecloudConfiguration settings,
                                                 FirecloudRunConfigurationEntry entry,
                                                 List<Long> entities,
                                                 Long projectId, Long configurationId, FireCloudClass rootEntityType) {
        PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(settings.getFirecloudLauncherImage());
        startVO.setConfigurationName(entry.getConfigName());
        startVO.setInstanceType(settings.getInstanceType());
        startVO.setHddSize(settings.getInstanceDisk());
        startVO.setRunSids(entry.getRunSids());
        //TODO: using only on demand instances??
        startVO.setIsSpot(false);
        startVO.setParams(entry.getParameters());
        String cmd = shouldCreateNewMethodConfig(entry)
                ? buildFirecloudLaunchCommandWithNewConfig(entry, settings, entities, projectId,
                configurationId, rootEntityType)
                : buildBasicFirecloudLaunchCommand(entry, settings, entities, projectId);
        startVO.setCmdTemplate(cmd);
        return startVO;
    }

    private boolean shouldCreateNewMethodConfig(FirecloudRunConfigurationEntry entry) {
        return CollectionUtils.isNotEmpty(entry.getMethodInputs())
                || CollectionUtils.isNotEmpty(entry.getMethodOutputs());
    }

    private void addCredentials(PipelineConfiguration conf, FirecloudConfiguration settings) {
        if (conf.getEnvironmentParams() == null) {
            conf.setEnvironmentParams(new HashMap<>());
        }
        conf.getEnvironmentParams().put(SystemParams.GS_OAUTH_REFRESH_TOKEN.getEnvName(),
                settings.refreshToken);
        conf.getEnvironmentParams().put(SystemParams.GS_OAUTH_CLIENT_ID.getEnvName(),
                settings.authClientId);
        conf.getEnvironmentParams().put(SystemParams.GS_OAUTH_CLIENT_SECRET.getEnvName(),
                settings.authClientSecret);
    }

    private String buildBasicFirecloudLaunchCommand(FirecloudRunConfigurationEntry entry,
                                                    FirecloudConfiguration settings,
                                                    List<Long> entities,
                                                    Long projectId) {
        List<String> template = buildFirecloudLaunchCommand(entry, settings, entities, projectId);
        template.add("--config");
        template.add(entry.getMethodConfigurationName() + "@" + entry.getMethodConfigurationSnapshot());
        return template.stream().collect(Collectors.joining(" "));
    }

    private String buildFirecloudLaunchCommandWithNewConfig(FirecloudRunConfigurationEntry entry,
                                                            FirecloudConfiguration settings,
                                                            List<Long> entities, Long projectId,
                                                            Long configurationId, FireCloudClass rootEntityType) {
        List<String> template = buildFirecloudLaunchCommand(entry, settings, entities, projectId);
        template.add("--config");
        template.add(StringUtils.defaultIfBlank(entry.getMethodConfigurationName(),
                generateDefaultConfigName(entry.getMethodName(), entry.getMethodSnapshot())));
        template.add("--api_config_id");
        template.add(configurationId.toString());
        template.add("--root_entity");
        template.add(rootEntityType.toString());
        return template.stream().collect(Collectors.joining(" "));
    }

    private String generateDefaultConfigName(String methodName, String snapshot) {
        return StringUtils.join(
                Arrays.asList(methodName, snapshot, "conf", PasswordGenerator.generateRandomString(5)), "-");
    }

    private List<String> buildFirecloudLaunchCommand(FirecloudRunConfigurationEntry entry,
                                                     FirecloudConfiguration settings,
                                                     List<Long> entities,
                                                     Long projectId) {
        List<String> template = new ArrayList<>();
        template.add(settings.getFirecloudLauncherCmd());
        template.add("--wnamespace");
        template.add(settings.getBillingProject());
        template.add("--method");
        template.add(entry.getMethodName() + "@" + entry.getMethodSnapshot());
        template.add("--entities_ids");
        template.add(String.format("\"%s\"", entities.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","))));
        template.add("--project-id");
        template.add(projectId.toString());
        template.add("--upload-data");
        template.add("--download-results");
        return template;
    }

    @Data
    @Builder
    private static class FirecloudConfiguration {

        private String firecloudLauncherCmd;
        private String firecloudLauncherImage;
        private String billingProject;
        private String authClientId;
        private String authClientSecret;
        private String refreshToken;
        private String instanceType;
        private Integer instanceDisk;
    }
}
