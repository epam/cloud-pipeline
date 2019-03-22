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

package com.epam.pipeline.entity;

import com.epam.pipeline.entity.utils.DateUtils;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * {@link BaseEntity} represents a basic com.epam.pipeline.entity with a {@link Long} id and a minimal
 * set of fields.
 */
@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
public class BaseEntity {
    /**
     * {@code Long} represents an com.epam.pipeline.entity's identifier.
     */
    private Long id;

    /**
     * {@code String} represents an com.epam.pipeline.entity's name.
     */
    private String name;

    private Date createdDate;

    public BaseEntity() {
        this.createdDate = new DateUtils().now();
    }

    public BaseEntity(final Long id, final String name) {
        this();
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("%s id: %s name: %s", getClass().getSimpleName(), id, name);
    }
}
