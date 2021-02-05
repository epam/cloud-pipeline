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

package com.epam.pipeline.test.creator.configuration;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ConfigurationCreatorUtils {

    public static final TypeReference<Result<PipelineConfiguration>> PIPELINE_CONFIGURATION_TYPE =
            new TypeReference<Result<PipelineConfiguration>>() {};
    public static final TypeReference<Result<List<ConfigurationEntry>>> CONFIGURATION_ENTRY_LIST_TYPE =
            new TypeReference<Result<List<ConfigurationEntry>>>() {};

    public static final TypeReference<Result<RunConfiguration>> RUN_CONFIGURATION_TYPE =
            new TypeReference<Result<RunConfiguration>>() {};
    public static final TypeReference<Result<List<RunConfiguration>>> RUN_CONFIGURATION_LIST_TYPE =
            new TypeReference<Result<List<RunConfiguration>>>() {};
    private static final List<AbstractRunConfigurationEntry> ENTRIES
            = Collections.singletonList(getRunConfigurationEntry());

    private ConfigurationCreatorUtils() {

    }

    public static RunConfiguration getRunConfiguration() {
        final RunConfiguration runConfiguration = new RunConfiguration();
        runConfiguration.setName(TEST_STRING);
        runConfiguration.setParent(new Folder(ID));
        runConfiguration.setDescription(TEST_STRING);
        runConfiguration.setOwner(TEST_STRING);
        runConfiguration.setEntries(ENTRIES);
        return runConfiguration;
    }

    public static RunConfiguration getRunConfiguration(Long id, String owner) {
        final RunConfiguration runConfiguration = new RunConfiguration();
        runConfiguration.setId(id);
        runConfiguration.setName(TEST_STRING);
        runConfiguration.setDescription(TEST_STRING);
        runConfiguration.setOwner(owner);
        runConfiguration.setEntries(ENTRIES);
        return runConfiguration;
    }

    public static RunConfigurationVO getRunConfigurationVO() {
        final RunConfigurationVO runConfigurationVO = new RunConfigurationVO();
        runConfigurationVO.setName(TEST_STRING);
        runConfigurationVO.setDescription(TEST_STRING);
        runConfigurationVO.setParentId(ID);
        runConfigurationVO.setEntries(ENTRIES);
        return runConfigurationVO;
    }

    public static RunConfigurationVO getRunConfigurationVO(Long id, Long parentId) {
        final RunConfigurationVO runConfigurationVO = new RunConfigurationVO();
        runConfigurationVO.setId(id);
        runConfigurationVO.setName(TEST_STRING);
        runConfigurationVO.setDescription(TEST_STRING);
        runConfigurationVO.setParentId(parentId);
        runConfigurationVO.setEntries(ENTRIES);
        return runConfigurationVO;
    }

    public static RunConfigurationEntry getRunConfigurationEntry() {
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setPipelineId(ID);
        return runConfigurationEntry;
    }

    public static ConfigurationEntry getConfigurationEntry() {
        return new ConfigurationEntry();
    }

    public static PipelineConfiguration getPipelineConfiguration() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setLanguage(TEST_STRING);
        return configuration;
    }

    public static PipelineConfiguration getPipelineConfiguration(final String image, final String instanceDisk,
                                                                 final boolean isSpot, final Long regionId) {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(image);
        configuration.setInstanceDisk(instanceDisk);
        configuration.setIsSpot(isSpot);
        configuration.setCloudRegionId(regionId);
        return configuration;
    }
}
