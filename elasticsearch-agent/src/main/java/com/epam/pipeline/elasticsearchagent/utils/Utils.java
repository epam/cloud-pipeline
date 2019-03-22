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
package com.epam.pipeline.elasticsearchagent.utils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public final class Utils {

    private Utils() {
        //
    }

    public static <T> T last(final List<T> collection) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException("Collection shall not by empty!");
        }
        return collection.get(collection.size() - 1);
    }
}
