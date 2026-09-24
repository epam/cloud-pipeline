/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearch.model.v7.action.support;

import com.epam.pipeline.elasticsearch.model.IndicesOptionsInner;

public final class IndicesOptionsFactoryV7 {

    private IndicesOptionsFactoryV7() {
        // no-op
    }

    public static IndicesOptionsInner lenientExpandOpen() {
        return new IndicesOptionsV7(true, true);
    }

    public static IndicesOptionsInner strictExpandOpen() {
        return new IndicesOptionsV7(false, false);
    }
}
