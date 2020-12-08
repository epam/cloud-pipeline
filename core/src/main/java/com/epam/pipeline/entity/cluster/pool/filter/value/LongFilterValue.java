/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster.pool.filter.value;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
public class LongFilterValue implements FilterValue<Long> {

    private Long value;

    public LongFilterValue(final Long value) {
        this.value = value;
    }

    @Override
    public boolean matches(final Long anotherValue) {
        return Objects.equals(getValue(), anotherValue);
    }

    @Override
    public boolean empty(final Long anotherValue) {
        return Objects.isNull(getValue());
    }
}
