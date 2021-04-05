package com.epam.pipeline.entity.git;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class GitEntryListing<T> {
    @JsonProperty("listing")
    private List<T> listing;

    @JsonProperty("page")
    private Long page;

    @JsonProperty("max_page")
    private Long maxPage;

    @JsonProperty("page_size")
    private Integer pageSize;
}
