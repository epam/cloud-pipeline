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

import com.epam.pipeline.entity.cluster.pool.filter.value.ValueMatcher;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

public enum PoolInstanceFilterOperator {
    EQUAL, NOT_EQUAL, EMPTY, NOT_EMPTY;

    public <T> boolean evaluate(final ValueMatcher<T> matcher, final T value) {
        return evaluate(matcher, Collections.singletonList(value));
    }

    public <T> boolean evaluate(final ValueMatcher<T> matcher, final Collection<T> values) {
        switch (this) {
            case EQUAL:
                return stream(values).anyMatch(matcher::matches);
            case NOT_EQUAL:
                return stream(values).allMatch(matcher::notMatches);
            case EMPTY:
                return stream(values).anyMatch(matcher::empty);
            case NOT_EMPTY:
                return stream(values).allMatch(matcher::notEmpty);
            default:
                throw new IllegalArgumentException("Unsupported operator type " + this);
        }
    }

    private <T> Stream<T> stream(final Collection<T> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Stream.empty();
        }
        return values.stream();
    }
}
