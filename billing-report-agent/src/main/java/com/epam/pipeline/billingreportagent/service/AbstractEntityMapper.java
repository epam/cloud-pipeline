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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractEntityMapper<T> {

    public abstract XContentBuilder map(EntityContainer<T> doc);

    public abstract String getBillingCenterKey();

    protected XContentBuilder buildUserContent(final EntityWithMetadata<PipelineUser> owner,
                                               final XContentBuilder jsonBuilder) throws IOException {
        final Optional<PipelineUser> user = Optional.ofNullable(owner)
                .map(EntityWithMetadata::getEntity);
        final Map<String, String> metadata = Optional.ofNullable(owner)
                .map(EntityWithMetadata::getMetadata)
                .orElseGet(Collections::emptyMap);
        return jsonBuilder
                .field("owner", user.map(PipelineUser::getUserName).orElse(null))
                .field("owner_user_id", user.map(PipelineUser::getId).orElse(null))
                .field("groups", user.map(PipelineUser::getGroups).orElse(null))
                .field("billing_center", metadata.get(getBillingCenterKey()));
    }
}
