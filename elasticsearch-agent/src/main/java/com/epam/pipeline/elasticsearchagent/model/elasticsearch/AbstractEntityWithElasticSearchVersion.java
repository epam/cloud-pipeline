package com.epam.pipeline.elasticsearchagent.model.elasticsearch;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class AbstractEntityWithElasticSearchVersion implements EntityWithElasticSearchVersion {
    protected final ElasticStackVersion version;

    protected AbstractEntityWithElasticSearchVersion(ElasticStackVersion version) {
        this.version = version;
    }

    @Override
    public ElasticStackVersion getElasticStack() {
        return this.version;
    }
}
