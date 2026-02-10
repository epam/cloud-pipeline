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

package com.epam.pipeline.elasticsearch.model.v7.action.delete;

import com.epam.pipeline.elasticsearch.model.DeleteRequestInner;
import lombok.Getter;

@Getter
@SuppressWarnings("PMD.ShortMethodName")
public class DeleteRequestV7 implements DeleteRequestInner {

    private final org.opensearch.action.delete.DeleteRequest inner;

    public DeleteRequestV7(final String indexName, final String objectId) {
        inner = new org.opensearch.action.delete.DeleteRequest(indexName, objectId);
    }

    @Override
    public String id() {
        return inner.id();
    }

    @Override
    public String index() {
        return inner.index();
    }

    @Override
    public String type() {
        throw new UnsupportedOperationException("Type is not supported for ElasticStack 7 version.");
    }
}
