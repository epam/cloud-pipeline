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

package com.epam.pipeline.entity.cluster.pool.filter;

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Data
public class PoolFilter {
    private final PoolFilterOperator operator;
    private final List<PoolInstanceFilter> filters;

    @JsonCreator
    public PoolFilter(
            @JsonProperty("operator") final PoolFilterOperator operator,
            @JsonProperty("filters") final List<PoolInstanceFilter> filters) {
        this.operator = operator;
        this.filters = filters;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return CollectionUtils.isEmpty(filters);
    }
}
