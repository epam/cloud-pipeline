package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;

@ApiModel(description = "Input describes Task input files.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TesInput {

    @ApiModelProperty(value = "")
    @JsonProperty("name")
    private String name;

    @ApiModelProperty(value = "")
    @JsonProperty("description")
    private String description;

    @ApiModelProperty(value = "REQUIRED, unless \"content\" is set.  URL in long term storage, for example: " +
            "s3://my-object-store/file1 gs://my-bucket/file2 file:///path/to/my/file /path/to/my/file etc...")
    @JsonProperty("url")
    private String url;

    @ApiModelProperty(value = "Path of the file inside the container. Must be an absolute path.")
    @JsonProperty("path")
    private String path;

    @ApiModelProperty(value = "Type of the file, FILE or DIRECTORY")
    @Valid
    @JsonProperty("type")
    private TesFileType type;

    @ApiModelProperty(value = "File content literal.  Implementations should support a minimum of 128 KiB " +
            "in this field and may define its own maximum. UTF-8 encoded  If content is not empty, \"url\" " +
            "must be ignored.")
    @JsonProperty("content")
    private String content;
}

