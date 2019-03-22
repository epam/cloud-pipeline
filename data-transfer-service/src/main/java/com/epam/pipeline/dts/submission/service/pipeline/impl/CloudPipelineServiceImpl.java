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

import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipeline;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipelineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CloudPipelineServiceImpl implements CloudPipelineService {

    private final int apiReadTimeout;
    private final int apiConnectTimeout;

    public CloudPipelineServiceImpl(@Value("${dts.pipeline.read.timeout:30}") int apiReadTimeout,
                                    @Value("${dts.pipeline.connect.timeout:10}") int apiConnectTimeout) {
        this.apiReadTimeout = apiReadTimeout;
        this.apiConnectTimeout = apiConnectTimeout;
    }

    @Override
    public void saveLogs(final Submission submission, final String logText) {
        getClient(submission)
                .saveRunLogs(
                        submission.getRunId(), submission.getState().getStatus(),
                        submission.getRunName(), logText);
    }

    @Override
    public void updateStatus(final Submission submission) {
        getClient(submission)
                .updateRunStatus(submission.getRunId(), submission.getState().getStatus());
    }

    @Override
    public void updateRunInstance(final Submission submission) {
        getClient(submission).updateInstance(submission.getRunId(), submission.getSubmissionHost());
    }

    private CloudPipeline getClient(final Submission submission) {
        return new CloudPipelineImpl(
                new CloudPipelineApiBuilder(apiConnectTimeout,
                        apiReadTimeout, submission.getApi(), submission.getToken()));
    }
}
