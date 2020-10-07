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

package com.epam.pipeline.test.configuration;

import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Folder;

import java.util.Collections;
import java.util.List;

public final class ConfigurationCreatorUtils {

    private static final String TEST_STRING = "TEST";
    private static final Long ID = 1L;
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

    public static RunConfigurationVO getRunConfigurationVO() {
        final RunConfigurationVO runConfigurationVO = new RunConfigurationVO();
        runConfigurationVO.setName(TEST_STRING);
        runConfigurationVO.setDescription(TEST_STRING);
        runConfigurationVO.setParentId(ID);
        runConfigurationVO.setEntries(ENTRIES);
        return runConfigurationVO;
    }

    public static RunConfigurationEntry getRunConfigurationEntry() {
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setPipelineId(ID);
        return runConfigurationEntry;
    }
}
