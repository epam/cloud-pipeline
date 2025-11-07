package com.epam.pipeline.elasticsearchagent.model.elasticsearch.request;

import com.epam.pipeline.elasticsearchagent.model.elasticsearch.AbstractEntityWithElasticSearchVersion;
import lombok.Builder;
import lombok.Getter;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.util.Map;

@Getter
@Builder
public class ElasticActionRequest extends AbstractEntityWithElasticSearchVersion {

    private static final String V6_DOC_MAPPING_TYPE = "_doc";

    private ElasticActionType action;
    private String index;
    private String id;
    private Map<String, Object> source;

    public org.elasticsearch.action.DocWriteRequest toElasticV6Request() {
        switch (action) {
            case INDEX:
                return new IndexRequest(index, V6_DOC_MAPPING_TYPE, id).source(source);
            case DELETE:
                return new DeleteRequest(index, V6_DOC_MAPPING_TYPE, id);
            default:
                throw new IllegalArgumentException(String.format("Unsupported action type: %s", action));
        }
    }

    public shaded.org.elasticsearch.v7.action.DocWriteRequest toElasticV7Request() {
        switch (action) {
            case INDEX:
                return new shaded.org.elasticsearch.v7.action.index.IndexRequest(index, id).source(source);
            case DELETE:
                return new shaded.org.elasticsearch.v7.action.delete.DeleteRequest(index, id);
            default:
                throw new IllegalArgumentException(String.format("Unsupported action type: %s", action));
        }
    }
}
