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

package com.epam.pipeline.dts.submission.service.execution;

import com.epam.pipeline.dts.submission.exception.SubmissionException;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionState;

public interface SubmissionScheduler {

    /**
     * Creates execution script for submission and executes it using qsub command
     * @param submission to run
     * @return SGE job id for scheduled submission
     */
    String schedule(Submission submission) throws SubmissionException;
    SubmissionState getState(Long submissionId) throws SubmissionException;
    String getLogs(Long submissionId) throws SubmissionException;
}
