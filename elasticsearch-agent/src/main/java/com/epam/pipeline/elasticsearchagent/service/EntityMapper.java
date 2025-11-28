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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public interface EntityMapper<T> {

    String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);
    String ATTRIBUTE_NAME = "Name";

    Map<String, ?> map(EntityContainer<T> doc);

    default String parseDataToString(Date date) {
        if (date == null) {
            return null;
        }
        return SIMPLE_DATE_FORMAT.format(date);
    }

    default void buildUserContent(final PipelineUser user,
                                  final Map<String, Object> jsonMap) {
        if (user != null) {
            final Map<String, String> attributes = user.getAttributes();
            jsonMap.put("ownerUserId", user.getId());
            jsonMap.put("ownerUserName", user.getUserName());
            jsonMap.put("ownerFriendlyName", MapUtils.emptyIfNull(attributes).get(ATTRIBUTE_NAME));
            jsonMap.put("ownerGroups", user.getGroups());
        }
    }

    default void buildMetadata(final Map<String, String> metadata,
                               final Map<String, Object> jsonMap) {
        buildMap(metadata, jsonMap, "metadata");
        jsonMap.putAll(MapUtils.emptyIfNull(metadata));
    }

    default void buildMap(final Map<String, String> data,
                          final Map<String, Object> jsonMap,
                          final String fieldName) {
        if (MapUtils.isNotEmpty(data)) {
            jsonMap.put(fieldName,
                    data.entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .toArray(String[]::new));
        }
    }

    default void buildPermissions(final PermissionsContainer permissions,
                                  final Map<String, Object> jsonMap) {
        jsonMap.put("allowed_users", permissions.getAllowedUsers().toArray());
        jsonMap.put("denied_users", permissions.getDeniedUsers().toArray());
        jsonMap.put("allowed_groups", permissions.getAllowedGroups().toArray());
        jsonMap.put("denied_groups", permissions.getDeniedGroups().toArray());
    }
}
