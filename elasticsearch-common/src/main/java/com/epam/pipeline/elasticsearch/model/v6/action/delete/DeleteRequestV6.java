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

package com.epam.pipeline.elasticsearch.model.v6.action.delete;

import com.epam.pipeline.elasticsearch.model.DeleteRequestInner;
import lombok.Getter;

@Getter
public class DeleteRequestV6 implements DeleteRequestInner {

    private static final String DOC_TYPE = "_doc";

    private final org.elasticsearch.action.delete.DeleteRequest inner;

    public DeleteRequestV6(final String indexName, final String type, final String objectId) {
        inner = new org.elasticsearch.action.delete.DeleteRequest(indexName, type, objectId);
    }

    public DeleteRequestV6(final String indexName, final String objectId) {
        inner = new org.elasticsearch.action.delete.DeleteRequest(indexName, DOC_TYPE, objectId);
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
        return inner.type();
    }
}
