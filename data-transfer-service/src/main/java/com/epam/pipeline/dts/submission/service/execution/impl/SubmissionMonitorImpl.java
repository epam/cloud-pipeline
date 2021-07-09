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

package com.epam.pipeline.dts.submission.service.execution.impl;

import com.epam.pipeline.dts.submission.exception.SubmissionException;
import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.model.execution.SGEJob;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionState;
import com.epam.pipeline.dts.submission.model.execution.SubmissionStatus;
import com.epam.pipeline.dts.submission.service.execution.SubmissionMonitor;
import com.epam.pipeline.dts.submission.service.execution.SubmissionScheduler;
import com.epam.pipeline.dts.submission.service.execution.SubmissionService;
import com.epam.pipeline.dts.submission.service.pipeline.CloudPipelineService;
import com.epam.pipeline.dts.submission.service.sge.SGEService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionMonitorImpl implements SubmissionMonitor {

    private final SubmissionService submissionService;
    private final SubmissionScheduler submissionScheduler;
    private final CloudPipelineService pipelineService;
    private final SGEService sgeService;

    @Override
    @Scheduled(fixedDelayString = "${dts.submission.statusPoll:60000}")
    public void checkSubmissions() {
        log.debug("Starting submissions status check");
        submissionService
                .loadActive()
                .forEach(this::checkSubmissionState);
        log.debug("Finished submissions status check");
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void checkSubmissionState(final Submission submission) {
        final Long submissionId = submission.getId();
        try {
            getAndUpdateState(submission, submissionId);
        } catch (Exception e) {
            log.error("An error occurred during submission {} status check: {}",
                     submissionId, e.getMessage());
        }
    }

    private void getAndUpdateState(final Submission submission, final Long submissionId) {
        try {
            final SubmissionState state = submissionScheduler.getState(submissionId);
            log.debug("Submission {} in state {}", submissionId, state);
            if (state.isFinal()) {
                completeSubmission(submission, state);
            } else if (StringUtils.isBlank(submission.getSubmissionHost())) {
                checkSubmissionHost(submission);
            }
        } catch (SubmissionException e) {
            log.error("Failed to get submission {} state: {}", submissionId, e.getMessage());
            final SubmissionState state = SubmissionState.builder()
                    .status(SubmissionStatus.FAILURE)
                    .reason(e.getMessage()).build();
            completeSubmission(submission, state);
        }
    }

    private void checkSubmissionHost(final Submission submission) {
        log.debug("Checking if submission {} is scheduled to some host", submission.getId());
        if (StringUtils.isBlank(submission.getJobId())) {
            log.error("SGE job id is missing for submission {}. Cannot get execution host.",
                    submission.getId());
            return;
        }
        try {
            SGEJob jobInfo = sgeService.getJobInfo(submission.getJobId());
            if (StringUtils.isNotBlank(jobInfo.getHost())) {
                log.debug("Retrieved hostname {} for submission {}", jobInfo.getHost(), submission.getId());
                submission.setSubmissionHost(jobInfo.getHost());
                submissionService.update(submission);
                pipelineService.updateRunInstance(submission);
            }
        } catch (SGECmdException e) {
            log.error("An error occurred during reading job info: {}", e.getMessage());
        }
    }

    private void completeSubmission(final Submission submission, final SubmissionState state) {
        log.debug("Finishing submission {} with state {}", submission.getId(), state);
        submission.updateState(state.getStatus(), state.getReason());
        submissionService.update(submission);
        saveLogs(submission);
        pipelineService.updateStatus(submission);
        log.debug("Successfully completed submission {}", submission.getId());
    }

    private void saveLogs(final Submission submission) {
        final String logs = getLogs(submission.getId());
        if (StringUtils.isNotBlank(logs)) {
            log.debug("Logs retrieved for submission {}", submission.getId());
            pipelineService.saveLogs(submission, logs);
        }
    }

    private String getLogs(final Long submissionId) {
        try {
            return submissionScheduler.getLogs(submissionId);
        } catch (SubmissionException e) {
            log.error("Failed to get submission {} logs: {}", submissionId, e.getMessage());
            return StringUtils.EMPTY;
        }
    }
}
