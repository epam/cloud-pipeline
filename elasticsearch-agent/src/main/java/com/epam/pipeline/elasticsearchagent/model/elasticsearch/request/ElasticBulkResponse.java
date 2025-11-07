package com.epam.pipeline.elasticsearchagent.model.elasticsearch.request;

import com.epam.pipeline.elasticsearchagent.model.elasticsearch.AbstractEntityWithElasticSearchVersion;
import com.epam.pipeline.elasticsearchagent.model.elasticsearch.ElasticStackVersion;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;


@Getter
@SuperBuilder
public class ElasticBulkResponse extends AbstractEntityWithElasticSearchVersion {

    private org.elasticsearch.action.bulk.BulkResponse v6Response;
    private shaded.org.elasticsearch.v7.action.bulk.BulkResponse v7Response;

    protected ElasticBulkResponse(ElasticStackVersion version) {
        super(version);
    }
}
