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

package com.epam.pipeline.entity.cluster.pool.filter.instancefilter;

import com.epam.pipeline.entity.cluster.pool.filter.value.MapValueMatcher;
import com.epam.pipeline.entity.cluster.pool.filter.value.ValueMatcher;

import java.util.Map;

public interface MapInstanceFilter extends PoolInstanceFilter<Map<String, String>> {

    @Override
    default ValueMatcher<Map<String, String>> getMatcher() {
        return new MapValueMatcher(getValue());
    }
}
