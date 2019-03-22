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

import com.epam.pipeline.dts.util.Utils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SubmissionCommandBuilder {

    private Path scriptFile;
    private Path logFile;
    private Integer cores;
    private String jobName;

    public SubmissionCommandBuilder withScriptFile(Path scriptFile) {
        this.scriptFile = scriptFile;
        return this;
    }

    public SubmissionCommandBuilder withLogFile(Path logFile) {
        this.logFile = logFile;
        return this;
    }

    public SubmissionCommandBuilder withCores(Integer cores) {
        this.cores = cores;
        return this;
    }

    public SubmissionCommandBuilder withJobName(String jobName) {
        this.jobName = jobName;
        return this;
    }

    public String build(String template) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("script", scriptFile.toString());
        parameters.put("log", logFile.toString());
        parameters.put("cores", cores.toString());
        parameters.put("job", jobName);
        return Utils.replaceParametersInTemplate(template, parameters);
    }
}
