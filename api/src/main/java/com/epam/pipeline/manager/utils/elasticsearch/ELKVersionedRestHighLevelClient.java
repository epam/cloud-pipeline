/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.utils.elasticsearch;

import com.epam.pipeline.entity.search.ElasticStackVersion;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;

import static java.util.Collections.emptySet;

public class ELKVersionedRestHighLevelClient extends RestHighLevelClient {

    public ELKVersionedRestHighLevelClient(RestClientBuilder restClientBuilder) {
        super(restClientBuilder);
    }

    public MultiSearchResponse msearch(final MultiSearchRequest searchRequest, final RequestOptions options,
            final ElasticStackVersion elasticStackVersion) throws IOException {
        return callBasedOnVersion(elasticStackVersion,
                () -> msearch(searchRequest, RequestOptions.DEFAULT),
                () -> performRequestAndParseEntity(searchRequest,
                        ElasticSearchRequestCommons::multiSearchRequestConverter, options,
                        MultiSearchResponse::fromXContext, emptySet())
                );
    }

    public BulkResponse bulk(final BulkRequest bulkRequest, final RequestOptions options,
                             final ElasticStackVersion elasticStackVersion) throws IOException {
        return callBasedOnVersion(elasticStackVersion,
                () -> bulk(bulkRequest, RequestOptions.DEFAULT),
                () -> performRequestAndParseEntity(bulkRequest,
                        ElasticSearchRequestCommons::bulkRequestConverter, options,
                        BulkResponse::fromXContent, emptySet())
        );
    }

    private <R> R callBasedOnVersion(ElasticStackVersion version, Caller <R> callOnV6, Caller <R> callOnV7)
            throws IOException {
        switch (version) {
            case V6:
                return callOnV6.apply();
            case V7:
                return callOnV7.apply();
            default:
                throw new UnsupportedOperationException("Provided version of ELK stack currently not supported.");
        }
    }

    @FunctionalInterface
    public interface Caller<R> {
        R apply() throws IOException;
    }

}
