package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


@ApiModel(description = "CreateTaskResponse describes a response from the CreateTask endpoint.")
@Data
public class TesCreateTaskResponse {
    @ApiModelProperty(value = "Task identifier assigned by the server.")
    @JsonProperty("id")
    private String id = null;
}

