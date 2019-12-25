package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(description = "OutputFileLog describes a single output file. This describes file details " +
        "after the task has completed successfully, for logging purposes.")
@Data
public class TesOutputFileLog {
    @ApiModelProperty(value = "URL of the file in storage, e.g. s3://bucket/file.txt")
    @JsonProperty("url")
    private String url;

    @ApiModelProperty(value = "Path of the file inside the container. Must be an absolute path.")
    @JsonProperty("path")
    private String path;

    @ApiModelProperty(value = "Size of the file in bytes.")
    @JsonProperty("size_bytes")
    private String sizeBytes;
}

