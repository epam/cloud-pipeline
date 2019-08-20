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

@Data
@ApiModel(description = "Executor describes a command to be executed, and its environment.")
public class TesExecutor {
    @ApiModelProperty(value = "Name of the container image, for example: " +
            "ubuntu quay.io/aptible/ubuntu gcr.io/my-org/my-image etc...")
    @JsonProperty("image")
    private String image = null;

    @ApiModelProperty(value = "A sequence of program arguments to execute, " +
            "where the first argument is the program to execute (i.e. argv).")
    @JsonProperty("command")
    @Valid
    private List<String> command = null;

    @ApiModelProperty(value = "The working directory that the command will be " +
            "executed in. Defaults to the directory set by the container image.")
    @JsonProperty("workdir")
    private String workdir = null;

    @ApiModelProperty(value = "Path inside the container to a file which will " +
            "be piped to the executor's stdin. Must be an absolute path.")
    @JsonProperty("stdin")
    private String stdin = null;

    @ApiModelProperty(value = "Path inside the container to a file where the " +
            "executor's stdout will be written to. Must be an absolute path.")
    @JsonProperty("stdout")
    private String stdout = null;

    @ApiModelProperty(value = "Path inside the container to a file where the " +
            "executor's stderr will be written to. Must be an absolute path.")
    @JsonProperty("stderr")
    private String stderr = null;

    @ApiModelProperty(value = "Enviromental variables to set within the container.")
    @JsonProperty("env")
    @Valid
    private Map<String, String> env = null;
}

