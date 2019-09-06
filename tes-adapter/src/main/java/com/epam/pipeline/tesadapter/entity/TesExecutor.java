package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@ApiModel(description = "Executor describes a command to be executed, and its environment.")
public class TesExecutor {
    @ApiModelProperty(value = "Name of the container image, for example: " +
            "ubuntu quay.io/aptible/ubuntu gcr.io/my-org/my-image etc...")
    @JsonProperty("image")
    private String image;

    @ApiModelProperty(value = "A sequence of program arguments to execute, " +
            "where the first argument is the program to execute (i.e. argv).")
    @JsonProperty("command")
    private List<String> command;

    @ApiModelProperty(value = "The working directory that the command will be " +
            "executed in. Defaults to the directory set by the container image.")
    @JsonProperty("workdir")
    private String workdir;

    @ApiModelProperty(value = "Path inside the container to a file which will " +
            "be piped to the executor's stdin. Must be an absolute path.")
    @JsonProperty("stdin")
    private String stdin;

    @ApiModelProperty(value = "Path inside the container to a file where the " +
            "executor's stdout will be written to. Must be an absolute path.")
    @JsonProperty("stdout")
    private String stdout;

    @ApiModelProperty(value = "Path inside the container to a file where the " +
            "executor's stderr will be written to. Must be an absolute path.")
    @JsonProperty("stderr")
    private String stderr;

    @ApiModelProperty(value = "Enviromental variables to set within the container.")
    @JsonProperty("env")
    private Map<String, String> env;
}

