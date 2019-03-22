/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface EventToRequestConverter {

    String INDEX_TYPE = "_doc";
    String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);
    String ATTRIBUTE_NAME = "Name";

    String getIndexPrefix();
    String getIndexName();

    default String buildIndexName() {
        return getIndexPrefix() + getIndexName();
    }

    List<DocWriteRequest> convertEventsToRequest(List<PipelineEvent> events,
                                                 String indexName);

    default DeleteRequest createDeleteRequest(PipelineEvent event, String indexName) {
        return new DeleteRequest(indexName, INDEX_TYPE, String.valueOf(event.getObjectId()));
    }

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
                    .field("ownerFriendlyName",
                            CollectionUtils.isEmpty(attributes) ? null : attributes.get(ATTRIBUTE_NAME))
                    .field("ownerGroups", user.getGroups());
        }
        return jsonBuilder;
    }

}
