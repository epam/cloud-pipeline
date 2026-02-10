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

package com.epam.pipeline.elasticsearch.model.v7.action;

import com.epam.pipeline.elasticsearch.model.DeleteRequest;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import com.epam.pipeline.elasticsearch.model.IndexRequest;
import com.epam.pipeline.elasticsearch.model.v7.action.delete.DeleteRequestV7;
import com.epam.pipeline.elasticsearch.model.v7.action.index.IndexRequestV7;

public final class DocWriterRequestFactoryV7 {

    private DocWriterRequestFactoryV7() {
        // no-op
    }

    public static org.opensearch.action.DocWriteRequest toRequest(final DocWriteRequest request) {
        if (request instanceof IndexRequest) {
            return ((IndexRequestV7) ((IndexRequest) request).getInner()).getInner();
        }
        if (request instanceof DeleteRequest) {
            return ((DeleteRequestV7) ((DeleteRequest) request).getInner()).getInner();
        }
        throw new UnsupportedOperationException();
    }
}
