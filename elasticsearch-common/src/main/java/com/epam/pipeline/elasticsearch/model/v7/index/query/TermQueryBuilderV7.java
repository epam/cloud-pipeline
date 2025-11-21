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
import lombok.Getter;

@Getter
public class TermQueryBuilderV7 implements TermQueryBuilder {

    private final shaded.org.elasticsearch.v7.index.query.TermQueryBuilder inner;

    public TermQueryBuilderV7(final String name, final String value) {
        inner = shaded.org.elasticsearch.v7.index.query.QueryBuilders.termQuery(name, value);
    }

    public TermQueryBuilderV7(final String name, final Long value) {
        inner = shaded.org.elasticsearch.v7.index.query.QueryBuilders.termQuery(name, value);
    }
}
