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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.DtsRunConfigurationEntry;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.DtsExecutionPreferences;
import com.epam.pipeline.exception.DtsRequestException;
import com.epam.pipeline.manager.dts.DtsRegistryManager;
import com.epam.pipeline.manager.dts.DtsSubmissionManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DtsConfigurationProviderImpl implements ConfigurationProvider<DtsRunConfigurationEntry,
        DtsExecutionPreferences> {

    private final DtsRunner dtsRunner;
    private final PipelineManager pipelineManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsSubmissionManager dtsSubmissionManager;
    private final MessageHelper messageHelper;

    @Override
    public ExecutionEnvironment getExecutionEnvironment() {
        return ExecutionEnvironment.DTS;
    }

    @Override
    public boolean hasNoPermission(DtsRunConfigurationEntry entry, String permissionName) {
        return false;
    }

    @Override
    public void validateEntry(DtsRunConfigurationEntry entry) {
        DtsRegistry dts = dtsRegistryManager.load(entry.getDtsId());
        Assert.isTrue(dts.isSchedulable(),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_NOT_SCHEDULABLE, dts.getName()));
        if (entry.getPipelineId() != null) {
            pipelineManager.load(entry.getPipelineId());
        }
    }

    @Override
    public List<PipelineRun> runAnalysis(AnalysisConfiguration<DtsRunConfigurationEntry> configuration) {
        return dtsRunner.runAnalysis(configuration);
    }

    @Override
    public boolean stop(Long runId, DtsExecutionPreferences executionPreferences) {
        DtsRegistry dts = dtsRegistryManager.load(executionPreferences.getDtsId());
        Assert.isTrue(dts.isSchedulable(),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_NOT_SCHEDULABLE, dts.getName()));
        try {
            dtsSubmissionManager.stopSubmission(dts.getId(), runId);
        } catch (DtsRequestException e) {
            log.error("Failed to stop submission on DTS: {}", e);
            return false;
        }

        return true;
    }
}
