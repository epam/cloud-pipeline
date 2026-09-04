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

package com.epam.pipeline.elasticsearch.model.v7.index.query;

import com.epam.pipeline.elasticsearch.model.TermQueryBuilder;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class TermQueryBuilderV7 implements TermQueryBuilder {

    private final Query inner;

    public TermQueryBuilderV7(final String name, final String value) {
        inner = Query.of(q -> q.term(t -> t.field(name).value(FieldValue.of(value))));
    }

    public TermQueryBuilderV7(final String name, final Long value) {
        inner = Query.of(q -> q.term(t -> t.field(name).value(FieldValue.of(value))));
    }

    @Override
    public Object getInner() {
        return inner;
    }
}
