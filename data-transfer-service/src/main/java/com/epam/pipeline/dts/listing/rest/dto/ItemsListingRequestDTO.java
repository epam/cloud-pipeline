package com.epam.pipeline.dts.listing.rest.dto;

import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemsListingRequestDTO {
    @JsonProperty(required = true)
    private Path path;
    @JsonProperty(required = true)
    private Integer pageSize;
    private String marker;
    private PipelineCredentials credentials;
}
