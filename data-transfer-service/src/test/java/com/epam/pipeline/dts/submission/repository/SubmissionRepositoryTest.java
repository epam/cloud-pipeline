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

package com.epam.pipeline.dts.submission.repository;

import com.epam.pipeline.dts.security.service.JwtTokenVerifier;
import com.epam.pipeline.dts.submission.model.execution.RunParameter;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@TestPropertySource(value={"classpath:test-application.properties"})
public class SubmissionRepositoryTest {

    @MockBean
    public JwtTokenVerifier jwtTokenVerifier;

    public static final long RUN_ID = 10L;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Test
    public void shouldFindExistingSubmissionByRunId() {
        Submission submission = saveSubmission(RUN_ID);
        Optional<Submission> loaded = submissionRepository.findByRunId(submission.getRunId());
        assertThat(loaded.isPresent(), is(true));
        Submission found = loaded.get();
        assertThat(found.getId(), equalTo(submission.getId()));
    }

    @Test
    public void shouldFindSubmissionsBySingleStatus() {
        Submission running = saveSubmission(RUN_ID);
        saveSubmission(RUN_ID + 1, SubmissionStatus.SUCCESS);
        saveSubmission(RUN_ID + 2, SubmissionStatus.STOPPED);
        saveSubmission(RUN_ID + 3, SubmissionStatus.FAILURE);
        assertThat(submissionRepository.count(), equalTo(4L));
        Collection<Submission> allRunning = submissionRepository
                .findAllByState_StatusIn(Collections.singletonList(SubmissionStatus.RUNNING));
        assertThat(allRunning, contains(running));
        assertThat(allRunning, hasSize(1));
    }

    @Test
    public void shouldFindSubmissionsByMultipleStatuses() {
        saveSubmission(RUN_ID);
        Submission success = saveSubmission(RUN_ID + 1, SubmissionStatus.SUCCESS);
        Submission stopped = saveSubmission(RUN_ID + 2, SubmissionStatus.STOPPED);
        Submission failure = saveSubmission(RUN_ID + 3, SubmissionStatus.FAILURE);
        assertThat(submissionRepository.count(), equalTo(4L));
        Collection<Submission> allRunning = submissionRepository.findAllByState_StatusIn(
                Arrays.asList(SubmissionStatus.SUCCESS, SubmissionStatus.STOPPED, SubmissionStatus.FAILURE));
        assertThat(allRunning, containsInAnyOrder(success, stopped, failure));
        assertThat(allRunning, hasSize(3));
    }

    private Submission saveSubmission(Long runId, SubmissionStatus status) {
        Submission submission = Submission.builder()
                .runId(runId)
                .jobId("10")
                .parameters(Collections.singletonList(new RunParameter("p1", "val1", "string")))
                .dockerImage("centos7")
                .api("localhost")
                .token("ABCD")
                .command("sleep infinity")
                .build();
        submission.updateState(status);
        return submissionRepository.save(submission);
    }

    private Submission saveSubmission(Long runId) {
        return saveSubmission(runId, SubmissionStatus.RUNNING);
    }
}
