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

package com.epam.pipeline.elasticsearch.model;

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.model.v6.index.query.BoolQueryBuilderV6;
import com.epam.pipeline.elasticsearch.model.v6.index.query.PrefixQueryBuilderV6;
import com.epam.pipeline.elasticsearch.model.v6.index.query.TermQueryBuilderV6;
import com.epam.pipeline.elasticsearch.model.v7.index.query.BoolQueryBuilderV7;
import com.epam.pipeline.elasticsearch.model.v7.index.query.PrefixQueryBuilderV7;
import com.epam.pipeline.elasticsearch.model.v7.index.query.TermQueryBuilderV7;

public final class QueryBuilders {

    private QueryBuilders() {
        // no-op
    }

    public static BoolQueryBuilder boolQuery(final ElasticStackVersion version) {
        switch (version) {
            case V7:
                return new BoolQueryBuilderV7();
            case V6:
            default:
                return new BoolQueryBuilderV6();
        }
    }

    public static TermQueryBuilder termQuery(final String name, final String value, final ElasticStackVersion version) {
        switch (version) {
            case V7:
                return new TermQueryBuilderV7(name, value);
            case V6:
            default:
                return new TermQueryBuilderV6(name, value);
        }
    }

    public static TermQueryBuilder termQuery(final String name, final Long value, final ElasticStackVersion version) {
        switch (version) {
            case V7:
                return new TermQueryBuilderV7(name, value);
            case V6:
            default:
                return new TermQueryBuilderV6(name, value);
        }
    }

    public static PrefixQueryBuilder prefixQuery(final String name, final String prefix,
                                                 final ElasticStackVersion version) {
        switch (version) {
            case V7:
                return new PrefixQueryBuilderV7(name, prefix);
            case V6:
            default:
                return new PrefixQueryBuilderV6(name, prefix);
        }
    }
}
