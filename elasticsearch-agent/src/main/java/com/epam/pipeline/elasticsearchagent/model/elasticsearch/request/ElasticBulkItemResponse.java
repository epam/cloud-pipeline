package com.epam.pipeline.elasticsearchagent.model.elasticsearch.request;

import com.epam.pipeline.elasticsearchagent.model.elasticsearch.AbstractEntityWithElasticSearchVersion;
import com.epam.pipeline.elasticsearchagent.model.elasticsearch.ElasticStackVersion;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class ElasticBulkItemResponse extends AbstractEntityWithElasticSearchVersion {

    private org.elasticsearch.action.bulk.BulkItemResponse v6Response;
    private shaded.org.elasticsearch.v7.action.bulk.BulkItemResponse v7Response;

    public ElasticBulkItemResponse(final ElasticStackVersion stackVersion) {
        super(stackVersion);
    }

}
