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
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.user.PipelineUser;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractEntityMapper<T> {

    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE);

    public abstract Map<String, ?> map(EntityContainer<T> doc);

    public abstract String getBillingCenterKey();

    protected void buildUserContent(final EntityWithMetadata<PipelineUser> owner,
                                    final Map<String, Object> jsonMap) {
        final Optional<PipelineUser> user = Optional.ofNullable(owner)
                .map(EntityWithMetadata::getEntity);
        final Map<String, String> metadata = Optional.ofNullable(owner)
                .map(EntityWithMetadata::getMetadata)
                .orElseGet(Collections::emptyMap);
        jsonMap.put("owner", user.map(PipelineUser::getUserName).orElse(null));
        jsonMap.put("owner_id", user.map(PipelineUser::getId).orElse(null));
        jsonMap.put("groups", user.map(PipelineUser::getGroups).orElse(null));
        jsonMap.put("billing_center", metadata.get(getBillingCenterKey()));
    }

    protected String asString(final Date date) {
        return Optional.ofNullable(date).map(SIMPLE_DATE_FORMAT::format).orElse(null);
    }

    protected String asString(final LocalDate date) {
        return Optional.ofNullable(date).map(LocalDate::toString).orElse(null);
    }
}
