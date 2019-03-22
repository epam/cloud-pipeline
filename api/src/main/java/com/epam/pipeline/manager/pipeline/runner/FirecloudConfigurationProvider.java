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

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.FirecloudPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FirecloudConfigurationProvider implements ConfigurationProvider<FirecloudRunConfigurationEntry,
        FirecloudPreferences> {
    private final FirecloudRunner firecloudRunner;

    @Override
    public ExecutionEnvironment getExecutionEnvironment() {
        return ExecutionEnvironment.FIRECLOUD;
    }

    @Override
    public boolean hasNoPermission(FirecloudRunConfigurationEntry entry, String permissionName) {
        return false;
    }

    @Override
    public void validateEntry(FirecloudRunConfigurationEntry entry) {
        // no-op
    }

    @Override
    public List<PipelineRun> runAnalysis(AnalysisConfiguration<FirecloudRunConfigurationEntry> configuration) {
        return firecloudRunner.runAnalysis(configuration);
    }

    @Override
    public boolean stop(Long runId, FirecloudPreferences executionPreferences) {
        return false;
    }
}
