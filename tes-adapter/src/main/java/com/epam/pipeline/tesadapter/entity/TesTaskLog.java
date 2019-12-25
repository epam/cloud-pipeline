package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@ApiModel(description = "TaskLog describes logging information related to a Task.")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TesTaskLog {
    @ApiModelProperty(value = "Logs for each executor")
    @JsonProperty("logs")
    private List<TesExecutorLog> logs;

    @ApiModelProperty(value = "Arbitrary logging metadata included by the implementation.")
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @ApiModelProperty(value = "When the task started, in RFC 3339 format.")
    @JsonProperty("start_time")
    private String startTime;

    @ApiModelProperty(value = "When the task ended, in RFC 3339 format.")
    @JsonProperty("end_time")
    private String endTime;

    @ApiModelProperty(value = "Information about all output files. " +
            "Directory outputs are flattened into separate items.")
    @JsonProperty("outputs")
    private List<TesOutputFileLog> outputs;

    @ApiModelProperty(value = "System logs are any logs the system decides are relevant, which are not tied " +
            "directly to an Executor process. Content is implementation specific: format, size, etc.  " +
            "System logs may be collected here to provide convenient access.  For example, the system may " +
            "include the name of the host where the task is executing, an error message that caused a SYSTEM_ERROR " +
            "state (e.g. disk is full), etc.  System logs are only included in the FULL task view.")
    @JsonProperty("system_logs")
    private List<String> systemLogs;
}

