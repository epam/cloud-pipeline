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
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionStatus;
import com.epam.pipeline.dts.submission.repository.SubmissionRepository;
import com.epam.pipeline.dts.submission.service.execution.SubmissionConverter;
import com.epam.pipeline.dts.submission.service.execution.SubmissionScheduler;
import com.epam.pipeline.dts.submission.service.execution.SubmissionService;
import com.epam.pipeline.dts.submission.service.sge.SGEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository repository;
    private final SubmissionScheduler scheduler;
    private final SubmissionConverter converter;
    private final SGEService sgeService;
    private final String dtsServiceName;

    public SubmissionServiceImpl(final SubmissionRepository repository,
                                 final SubmissionScheduler scheduler,
                                 final SubmissionConverter converter,
                                 final SGEService sgeService,
                                 @Value("${dts.service.name}") final String dtsServiceName) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.converter = converter;
        this.sgeService = sgeService;
        this.dtsServiceName = dtsServiceName;
    }

    @Override
    public Submission create(Submission submission) {
        converter.validate(submission);
        submission.updateState(SubmissionStatus.RUNNING, "Scheduled new submission.");
        submission.setJobName(getJobName(submission));
        repository.save(submission);
        try {
            final String jobId = scheduler.schedule(submission);
            submission.updateState(SubmissionStatus.RUNNING, String.format("Created SGE job with id %s", jobId));
            submission.setJobId(jobId);
        } catch (SubmissionException e) {
            submission.updateState(SubmissionStatus.FAILURE, e.getMessage());
        }
        return repository.save(submission);
    }

    @Override
    public Submission update(Submission submission) {
        load(submission.getId());
        converter.validate(submission);
        return repository.save(submission);
    }

    @Override
    public Submission load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Failed to find submission by id: %s.", id)));
    }

    @Override
    public Submission loadByRunId(Long runId) {
        return repository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Failed to find submission by runId: %s.", runId)));
    }

    @Override
    public Submission stop(Long id) {
        final Submission submission = loadByRunId(id);
        final String jobId = submission.getJobId();
        try {
            sgeService.stopJob(submission.getJobId());
            submission.updateState(SubmissionStatus.STOPPED, String.format("Stopped SGE job with id %s", jobId));
            submission.setJobId(jobId);
        } catch (SGECmdException e) {
            submission.updateState(SubmissionStatus.FAILURE, e.getMessage());
            log.error(String.format("Failed to stop SGE job with id %s due to: %s", jobId, e.getMessage()), e);
        }
        return repository.save(submission);
    }

    @Override
    public Collection<Submission> loadActive() {
        return repository.findAllByState_StatusIn(
                Arrays.stream(SubmissionStatus.values())
                        .filter(status -> !status.isFinalStatus())
                        .collect(Collectors.toList()));
    }

    private String getJobName(final Submission submission) {
        return Optional.ofNullable(submission.getJobName())
                .orElse(String.join("-",  dtsServiceName, submission.getRunName()));
    }
}
