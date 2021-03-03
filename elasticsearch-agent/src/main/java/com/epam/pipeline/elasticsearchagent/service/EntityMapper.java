/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.service;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public interface EntityMapper<T> {

    String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);
    String ATTRIBUTE_NAME = "Name";

    XContentBuilder map(EntityContainer<T> doc);

    default String parseDataToString(Date date) {
        if (date == null) {
            return null;
        }
        return SIMPLE_DATE_FORMAT.format(date);
    }

    default XContentBuilder buildUserContent(final PipelineUser user,
                                             final XContentBuilder jsonBuilder) throws IOException {
        if (user != null) {
            Map<String, String> attributes = user.getAttributes();
            jsonBuilder
                    .field("ownerUserId", user.getId())
                    .field("ownerUserName", user.getUserName())
                    .field("ownerFriendlyName", MapUtils.emptyIfNull(attributes).get(ATTRIBUTE_NAME))
                    .field("ownerGroups", user.getGroups());
        }
        return jsonBuilder;
    }

    default XContentBuilder buildMetadata(final Map<String, String> metadata,
                                          final XContentBuilder jsonBuilder) throws IOException {
        buildMap(metadata, jsonBuilder, "metadata");
        MapUtils.emptyIfNull(metadata).forEach((key, value) -> {
            try {
                jsonBuilder.field(key, value);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to add metadata to elasticsearch document: ", e);
            }
        });
        return jsonBuilder;
    }

    default XContentBuilder buildMap(final Map<String, String> data,
                                     final XContentBuilder jsonBuilder,
                                     final String fieldName) throws IOException {
        if (MapUtils.isNotEmpty(data)) {
            jsonBuilder.array(fieldName,
                    data.entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .toArray(String[]::new));
        }
        return jsonBuilder;
    }

    default XContentBuilder buildPermissions(final PermissionsContainer permissions,
                                             final XContentBuilder jsonBuilder) throws IOException {
        jsonBuilder.array("allowed_users", permissions.getAllowedUsers().toArray());
        jsonBuilder.array("denied_users", permissions.getDeniedUsers().toArray());
        jsonBuilder.array("allowed_groups", permissions.getAllowedGroups().toArray());
        jsonBuilder.array("denied_groups", permissions.getDeniedGroups().toArray());
        return jsonBuilder;
    }
}
