/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vo;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class SecuredEntityVO {

    @JsonProperty("id")
    private final long entityId;
    @JsonProperty("class")
    private final String entityClass;

    @JsonCreator
    private SecuredEntityVO(@JsonProperty("id") long entityId,
                            @JsonProperty("class") String entityClass) {
        this.entityId = entityId;
        this.entityClass = entityClass;
    }

    public static SecuredEntityVO from(final Class<? extends AbstractSecuredEntity> clazz, final long id) {
        return new SecuredEntityVO(id, clazz.getSimpleName());
    }

    public static SecuredEntityVO from(final AbstractSecuredEntity entity) {
        return new SecuredEntityVO(entity.getId(), entity.getClass().getSimpleName());
    }
}
