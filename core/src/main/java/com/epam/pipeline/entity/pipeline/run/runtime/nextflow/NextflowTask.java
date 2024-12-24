/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.pipeline.run.runtime.nextflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NextflowTask {
    @JsonProperty("task_id")
    private String id;
    private String hash;

    @JsonProperty("native_id")
    private String nativeId;
    private String process;
    private String tag;
    private String name;
    private String status;

    @JsonProperty("exit")
    private String exitCode;

    private String module;
    private String container;

    @JsonProperty("cpus")
    private String cpusRequested;

    @JsonProperty("time")
    private String timeRequested;

    @JsonProperty("disk")
    private String diskRequested;

    @JsonProperty("memory")
    private String memoryRequested;

    private String attempt;

    @JsonProperty("submit")
    private String submitTimestamp;

    @JsonProperty("start")
    private String startTimestamp;

    @JsonProperty("complete")
    private String completeTimestamp;

    private String duration;
    private String realtime;
    private String queue;

    @JsonProperty("%cpu")
    private String cpuUtilization;

    @JsonProperty("%mem")
    private String memUtilization;

    private String rss;
    private String vmem;

    @JsonProperty("peak_rss")
    private String peakRss;

    @JsonProperty("peak_vmem")
    private String peakVmem;

    private String rchar;
    private String wchar;
    private String workdir;
    private String script;
}
