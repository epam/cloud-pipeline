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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class StorageRequestMapper {

    public Map<String, ?> map(final StorageRequest requests) {
        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("created_date", asString(requests.getCreatedDate()));
        jsonMap.put("user", requests.getUser().getUserName());
        jsonMap.put("user_id", requests.getUser().getId());
        jsonMap.put("storage_id", requests.getStorageId());
        jsonMap.put("storage_name", requests.getStorageName());
        jsonMap.put("read_requests", requests.getReadRequests());
        jsonMap.put("write_requests", requests.getWriteRequests());
        jsonMap.put("total_requests", requests.getTotalRequests());
        jsonMap.put("period", asString(requests.getPeriod()));
        return jsonMap;
    }

    private String asString(final LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime).map(LocalDateTime::toString).orElse(null);
    }
}
