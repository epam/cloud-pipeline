package com.epam.pipeline.elasticsearchagent.model.elasticsearch.response;

import com.epam.pipeline.elasticsearchagent.model.elasticsearch.AbstractEntityWithElasticSearchVersion;
import lombok.Builder;

@Builder
public class SearchHit extends AbstractEntityWithElasticSearchVersion {

    private org.elasticsearch.search.SearchHit hitV6;
    private shaded.org.elasticsearch.v7.search.SearchHit hitV7;

}
