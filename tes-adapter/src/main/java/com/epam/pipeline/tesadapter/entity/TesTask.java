package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@ApiModel(description = "Task describes an instance of a task.")
@Data
@Builder
public class TesTask {
    @ApiModelProperty(value = "Task identifier assigned by the server.")
    @JsonProperty("id")
    private String id;

    @ApiModelProperty(value = "")
    @JsonProperty("state")
    private TesState state;

    @ApiModelProperty(value = "")
    @JsonProperty("name")
    private String name;

    @ApiModelProperty(value = "")
    @JsonProperty("description")
    private String description;

    @ApiModelProperty(value = "Input files. Inputs will be downloaded and mounted into the executor container.")
    @JsonProperty("inputs")
    private List<TesInput> inputs;

    @ApiModelProperty(value = "Output files. Outputs will be uploaded from the executor container " +
            "to long-term storage.")
    @JsonProperty("outputs")
    private List<TesOutput> outputs;

    @ApiModelProperty(value = "Request that the task be run with these resources.")
    @JsonProperty("resources")
    private TesResources resources;

    @ApiModelProperty(value = "A list of executors to be run, sequentially. Execution stops on the first error.")
    @JsonProperty("executors")
    private List<TesExecutor> executors;

    @ApiModelProperty(value = "Volumes are directories which may be used to share data between Executors. " +
            "Volumes are initialized as empty directories by the system when the task starts and are mounted " +
            "at the same path in each Executor.  For example, given a volume defined at \"/vol/A\", executor 1" +
            " may write a file to \"/vol/A/exec1.out.txt\", then executor 2 may read from that file.  " +
            "(Essentially, this translates to a `docker run -v` flag where the container path is the same " +
            "for each executor).")
    @JsonProperty("volumes")
    private List<String> volumes;

    @ApiModelProperty(value = "A key-value map of arbitrary tags.")
    @JsonProperty("tags")
    private Map<String, String> tags;

    @ApiModelProperty(value = "Task logging information. Normally, this will contain only one entry, but in the" +
            " case where a task fails and is retried, an entry will be appended to this list.")
    @JsonProperty("logs")
    private List<TesTaskLog> logs;

    @ApiModelProperty(value = "Date + time the task was created, in RFC 3339 format. This is set by the system, " +
            "not the client.")
    @JsonProperty("creation_time")
    private String creationTime;
}

