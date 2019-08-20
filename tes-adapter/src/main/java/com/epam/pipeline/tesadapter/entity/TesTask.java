package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ApiModel(description = "Task describes an instance of a task.")
@Data
public class TesTask {
    @ApiModelProperty(value = "Task identifier assigned by the server.")
    @JsonProperty("id")
    private String id = null;

    @ApiModelProperty(value = "")
    @Valid
    @JsonProperty("state")
    private TesState state = null;

    @ApiModelProperty(value = "")
    @JsonProperty("name")
    private String name = null;

    @ApiModelProperty(value = "")
    @JsonProperty("description")
    private String description = null;

    @ApiModelProperty(value = "Input files. Inputs will be downloaded and mounted into the executor container.")
    @JsonProperty("inputs")
    @Valid
    private List<TesInput> inputs = null;

    @ApiModelProperty(value = "Output files. Outputs will be uploaded from the executor container to long-term storage.")
    @JsonProperty("outputs")
    @Valid
    private List<TesOutput> outputs = null;

    @ApiModelProperty(value = "Request that the task be run with these resources.")
    @JsonProperty("resources")
    @Valid
    private TesResources resources = null;

    @ApiModelProperty(value = "A list of executors to be run, sequentially. Execution stops on the first error.")
    @JsonProperty("executors")
    @Valid
    private List<TesExecutor> executors = null;

    @ApiModelProperty(value = "Volumes are directories which may be used to share data between Executors. " +
            "Volumes are initialized as empty directories by the system when the task starts and are mounted " +
            "at the same path in each Executor.  For example, given a volume defined at \"/vol/A\", executor 1" +
            " may write a file to \"/vol/A/exec1.out.txt\", then executor 2 may read from that file.  " +
            "(Essentially, this translates to a `docker run -v` flag where the container path is the same " +
            "for each executor).")
    @JsonProperty("volumes")
    @Valid
    private List<String> volumes = null;

    @ApiModelProperty(value = "A key-value map of arbitrary tags.")
    @JsonProperty("tags")
    @Valid
    private Map<String, String> tags = null;
    @ApiModelProperty(value = "Task logging information. Normally, this will contain only one entry, but in the" +
            " case where a task fails and is retried, an entry will be appended to this list.")
    @JsonProperty("logs")
    @Valid
    private List<TesTaskLog> logs = null;

    @ApiModelProperty(value = "Date + time the task was created, in RFC 3339 format. This is set by the system, " +
            "not the client.")
    @JsonProperty("creation_time")
    private String creationTime = null;
}

