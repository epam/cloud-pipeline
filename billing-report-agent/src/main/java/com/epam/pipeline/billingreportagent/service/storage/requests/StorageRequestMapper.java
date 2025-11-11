/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.storage.requests;

import com.epam.pipeline.billingreportagent.model.storage.requests.StorageRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StorageRequestMapper {

    public XContentBuilder map(final StorageRequest requests) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            return jsonBuilder.startObject()
                    .field("created_date", requests.getCreatedDate())
                    .field("user", requests.getUser().getUserName())
                    .field("user_id", requests.getUser().getId())
                    .field("storage_id", requests.getStorageId())
                    .field("storage_name", requests.getStorageName())
                    .field("read_requests", requests.getReadRequests())
                    .field("write_requests", requests.getWriteRequests())
                    .field("total_requests", requests.getTotalRequests())
                    .field("period", requests.getPeriod())
                    .endObject();
        } catch (
                IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for storage requests: ", e);
        }
    }
}
