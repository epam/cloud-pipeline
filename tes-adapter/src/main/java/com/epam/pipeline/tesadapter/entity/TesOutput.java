package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;

@ApiModel(description = "Output describes Task output files.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TesOutput {
    @ApiModelProperty(value = "")
    @JsonProperty("name")
    private String name;

    @ApiModelProperty(value = "")
    @JsonProperty("description")
    private String description;

    @ApiModelProperty(value = "URL in long term storage, for example: s3://my-object-store/file1 " +
            "gs://my-bucket/file2 file:///path/to/my/file /path/to/my/file etc...")
    @JsonProperty("url")
    private String url;

    @ApiModelProperty(value = "Path of the file inside the container. Must be an absolute path.")
    @JsonProperty("path")
    private String path;

    @ApiModelProperty(value = "Type of the file, FILE or DIRECTORY")
    @JsonProperty("type")
    @Valid
    private TesFileType type;
}

