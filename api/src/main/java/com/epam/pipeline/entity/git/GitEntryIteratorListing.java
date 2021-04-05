package com.epam.pipeline.entity.git;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class GitEntryIteratorListing<T> {

    @JsonProperty("listing")
    private List<T> listing;

    @JsonProperty("page")
    private Long page;

    @JsonProperty("page_size")
    private Integer pageSize;

    @JsonProperty("has_next")
    private Boolean hasNext;
}
