package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NonNull;

import javax.validation.Valid;
import java.util.List;

@ApiModel(description = "ListTasksResponse describes a response from the ListTasks endpoint.")
@Data
public class TesListTasksResponse {
    @ApiModelProperty(value = "List of tasks.")
    @JsonProperty("tasks")
    @Valid
    @NonNull
    private List<TesTask> tasks;

    @ApiModelProperty(value = "Token used to return the next page of results. See TaskListRequest.next_page_token")
    @JsonProperty("next_page_token")
    private String nextPageToken;
}

