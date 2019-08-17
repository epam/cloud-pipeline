package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ListTasksResponse describes a response from the ListTasks endpoint.
 */
@ApiModel(description = "ListTasksResponse describes a response from the ListTasks endpoint.")
@Validated
@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-08-17T00:22:00.237+03:00")

public class TesListTasksResponse   {
  @JsonProperty("tasks")
  @Valid
  private List<TesTask> tasks = null;

  @JsonProperty("next_page_token")
  private String nextPageToken = null;

  public TesListTasksResponse tasks(List<TesTask> tasks) {
    this.tasks = tasks;
    return this;
  }

  public TesListTasksResponse addTasksItem(TesTask tasksItem) {
    if (this.tasks == null) {
      this.tasks = new ArrayList<TesTask>();
    }
    this.tasks.add(tasksItem);
    return this;
  }

  /**
   * List of tasks.
   * @return tasks
  **/
  @ApiModelProperty(value = "List of tasks.")

  @Valid

  public List<TesTask> getTasks() {
    return tasks;
  }

  public void setTasks(List<TesTask> tasks) {
    this.tasks = tasks;
  }

  public TesListTasksResponse nextPageToken(String nextPageToken) {
    this.nextPageToken = nextPageToken;
    return this;
  }

  /**
   * Token used to return the next page of results. See TaskListRequest.next_page_token
   * @return nextPageToken
  **/
  @ApiModelProperty(value = "Token used to return the next page of results. See TaskListRequest.next_page_token")


  public String getNextPageToken() {
    return nextPageToken;
  }

  public void setNextPageToken(String nextPageToken) {
    this.nextPageToken = nextPageToken;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TesListTasksResponse tesListTasksResponse = (TesListTasksResponse) o;
    return Objects.equals(this.tasks, tesListTasksResponse.tasks) &&
        Objects.equals(this.nextPageToken, tesListTasksResponse.nextPageToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tasks, nextPageToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TesListTasksResponse {\n");
    
    sb.append("    tasks: ").append(toIndentedString(tasks)).append("\n");
    sb.append("    nextPageToken: ").append(toIndentedString(nextPageToken)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

