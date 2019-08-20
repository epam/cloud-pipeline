package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "ExecutorLog describes logging information related to an Executor.")
public class TesExecutorLog {
    @ApiModelProperty(value = "Time the executor started, in RFC 3339 format.")
    @JsonProperty("start_time")
    private String startTime;

    @ApiModelProperty(value = "Time the executor ended, in RFC 3339 format.")
    @JsonProperty("end_time")
    private String endTime;

    @ApiModelProperty(value = "Stdout content.  This is meant for convenience. No guarantees are made about " +
            "the content. Implementations may chose different approaches: only the head, only the tail, a URL " +
            "reference only, etc.  In order to capture the full stdout users should set Executor.stdout to a " +
            "container file path, and use Task.outputs to upload that file to permanent storage.")
    @JsonProperty("stdout")
    private String stdout;

    @ApiModelProperty(value = "Stderr content.  This is meant for convenience. No guarantees are made about " +
            "the content. Implementations may chose different approaches: only the head, only the tail, a URL " +
            "reference only, etc.  In order to capture the full stderr users should set Executor.stderr to a " +
            "container file path, and use Task.outputs to upload that file to permanent storage.")
    @JsonProperty("stderr")
    private String stderr;

    @ApiModelProperty(value = "Exit code.")
    @JsonProperty("exit_code")
    private Integer exitCode;
}

