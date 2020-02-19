/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractEntityMapper<T> {

    public abstract XContentBuilder map(EntityContainer<T> doc);
    public abstract String getBillingCenterKey();

    protected XContentBuilder buildUserContent(final PipelineUser user,
                                               final Map<String, String> userMetadata,
                                               final XContentBuilder jsonBuilder) throws IOException {
        if (user != null) {
            jsonBuilder
                    .field("owner", user.getUserName())
                    .field("groups", user.getGroups())
                    .field("billing_center", MapUtils.emptyIfNull(userMetadata).get(getBillingCenterKey()));
        }
        return jsonBuilder;
    }
}
