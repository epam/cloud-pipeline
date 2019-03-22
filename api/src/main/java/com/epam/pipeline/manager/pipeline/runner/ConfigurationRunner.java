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

import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link ConfigurationRunner} provides methods to launch execution of a {@link RunConfiguration}.
 * It optionally supports run parameters resolving from input {@link MetadataEntity} instances.
 * Delegates actual analysis scheduling to matching {@link ExecutionRunner}
 */
@Service
public class ConfigurationRunner {


    @Autowired
    private RunConfigurationManager configurationManager;

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private AbstractRunConfigurationMapper runConfigurationMapper;

    @Autowired
    private ConfigurationProviderManager configurationProvider;

    /**
     * Schedules execution of a {@link RunConfiguration} and creates a number
     * of associated {@link PipelineRun} instances. Default values of {@link RunConfiguration}
     * may be overriden by {@code runConfiguration} parameter. If {@code entitiesIds} or {@code metadataClass}
     * are passed method will try to resolve {@link RunConfiguration} parameters according to
     * {@link MetadataEntity} instances. For {@code metadataClass} method will try to find
     * {@link MetadataEntity} instances in the whole project with matching {@link MetadataClass}.
     * For each resolved {@link MetadataEntity} instance a separate {@link PipelineRun} or several ones
     * will be created.
     *
     * @param refreshToken      authorization token for Firecloud
     * @param runConfiguration  to run. Must specify {@code id} parameter of existing in DB
     *                          {@link RunConfiguration} instance. Any other parameter may be specified,
     *                          in this case it will override the same parameter from DB instance.
     *                          For cluster (producing several {@link PipelineRun}) configurations if a list of
     *                          {@link RunConfigurationEntry} is passed in {@code entries} field,
     *                          only entries from this list will be scheduled. If {@code entitiesIds} are specified in
     *                          {@link RunConfigurationWithEntitiesVO} for each entity id a separate run(s)
     *                          of {@link RunConfiguration} will be scheduled. If {@code metadataClass} is set
     *                          a separate run(s) will be scheduled for each {@link MetadataEntity} instance
     *                          with a matching {@link MetadataClass} in current project.
     * @param expansionExpression   expression to convert actual entities from {@code entitiesIds}
     *                              to required {@code rootEntityId} specified in {@link RunConfigurationEntry}
     * @return list of scheduled {@link PipelineRun}
     */
    public List<PipelineRun> runConfiguration(String refreshToken,
                                              RunConfigurationWithEntitiesVO runConfiguration,
                                              String expansionExpression) {
        RunConfiguration dbConfiguration = configurationManager.load(runConfiguration.getId());
        RunConfiguration configuration = mergeRunConfigurations(dbConfiguration, runConfigurationMapper
                .toRunConfiguration(runConfiguration));
        configurationManager.validateConfiguration(configuration);
        configuration.getEntries().forEach(entry -> configurationProvider.assertExecutionEnvironment(entry));

        List<Long> entitiesIds = getIdsToProcess(runConfiguration);
        return configuration.getEntries().stream()
                .collect(Collectors.groupingBy(AbstractRunConfigurationEntry::getExecutionEnvironment))
                .entrySet()
                .stream()
                .map(env -> {
                    AnalysisConfiguration<AbstractRunConfigurationEntry> conf = AnalysisConfiguration
                            .builder()
                            .configurationId(configuration.getId())
                            .entries(env.getValue())
                            .entitiesIds(entitiesIds)
                            .expansionExpression(expansionExpression)
                            .refreshToken(refreshToken)
                            .build();
                    return configurationProvider.runAnalysis(conf);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<Long> getIdsToProcess(RunConfigurationWithEntitiesVO runConfiguration) {
        if (CollectionUtils.isNotEmpty(runConfiguration.getEntitiesIds())) {
            return runConfiguration.getEntitiesIds();
        }
        if (StringUtils.hasText(runConfiguration.getMetadataClass())) {
            Folder folder = folderManager.load(runConfiguration.getFolderId());
            Assert.notNull(folder, "Project is required to launch whole class processing.");
            MetadataClass metadataClass =
                    metadataEntityManager.loadClass(runConfiguration.getMetadataClass());
            return metadataEntityManager
                    .loadMetadataEntityByClassNameAndFolderId(folder.getId(), metadataClass.getName())
                    .stream()
                    .map(BaseEntity::getId)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    //method allows overriding of db registered configuration entries, but doesn't add new ones
    private RunConfiguration mergeRunConfigurations(RunConfiguration dbConfiguration, RunConfiguration configuration) {
        List<AbstractRunConfigurationEntry> mergedEntries = configurationProvider
                .mergeConfigurationEntry(configuration.getEntries(), dbConfiguration.getEntries());
        dbConfiguration.setEntries(mergedEntries);
        return dbConfiguration;
    }


}
