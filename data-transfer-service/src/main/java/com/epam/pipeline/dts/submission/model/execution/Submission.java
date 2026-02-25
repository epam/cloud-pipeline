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

package com.epam.pipeline.dts.submission.model.execution;

import com.epam.pipeline.dts.util.Utils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Builder
@AllArgsConstructor
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    @Column(unique = true)
    private Long runId;
    @Column(length = Integer.MAX_VALUE)
    private String runName;
    private String jobId;
    private String submissionHost;
    @Column(length = Integer.MAX_VALUE)
    private String jobName;
    private Integer cores;
    @Embedded
    private List<RunParameter> parameters;
    private String dockerImage;
    private String api;
    @Column(length = Integer.MAX_VALUE)
    private String token;
    @Column(length = Integer.MAX_VALUE)
    private String command;
    private Integer coresNumber;
    @Embedded
    private SubmissionState state;

    public void updateState(SubmissionStatus status) {
        updateState(status, null);
    }

    public void updateState(SubmissionStatus status, String reason) {
        this.state = SubmissionState.builder()
                .status(status)
                .reason(reason)
                .timestamp(Utils.now()).build();
    }

}
