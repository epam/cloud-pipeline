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

package com.epam.pipeline.dts.submission.service.pipeline.impl;

import com.epam.pipeline.dts.submission.model.execution.SubmissionStatus;
import com.epam.pipeline.dts.submission.model.pipeline.RunInstance;
import com.epam.pipeline.dts.submission.model.pipeline.RunLog;
import com.epam.pipeline.dts.submission.model.pipeline.StatusUpdate;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipeline;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.dts.util.QueryUtils;
import com.epam.pipeline.dts.util.Utils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CloudPipelineImpl implements CloudPipeline {

    private final CloudPipelineApiBuilder apiBuilder;

    @Override
    public void saveRunLogs(final Long runId, final SubmissionStatus status,
                            final String taskName,
                            final String logText) {
        final RunLog log = RunLog.builder()
                .status(status)
                .logText(logText)
                .taskName(taskName)
                .date(Utils.currentDate())
                .build();
        QueryUtils.execute(apiBuilder.buildClient().saveLogs(runId, log));
    }

    @Override
    public void updateRunStatus(final Long runId, final SubmissionStatus status) {
        final StatusUpdate statusUpdate = StatusUpdate.builder()
                .endDate(Utils.currentDate())
                .status(status)
                .build();
        QueryUtils.execute(apiBuilder.buildClient().updateRunStatus(runId, statusUpdate));
    }

    @Override
    public void updateInstance(final Long runId, final String hostname) {
        final RunInstance instance = RunInstance.builder().nodeName(hostname).build();
        QueryUtils.execute(apiBuilder.buildClient().updateRunInstance(runId, instance));
    }
}
