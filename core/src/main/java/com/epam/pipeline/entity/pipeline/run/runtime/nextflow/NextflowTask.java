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
