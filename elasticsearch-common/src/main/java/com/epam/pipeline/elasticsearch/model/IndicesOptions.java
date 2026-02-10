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

package com.epam.pipeline.elasticsearch.model;

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.model.v6.action.support.IndicesOptionsFactoryV6;
import com.epam.pipeline.elasticsearch.model.v7.action.support.IndicesOptionsFactoryV7;

public final class IndicesOptions {

    private IndicesOptions() {
        // no-op
    }

    public static IndicesOptionsInner lenientExpandOpen(final ElasticStackVersion version) {
        if (ElasticStackVersion.V6.equals(version)) {
            return IndicesOptionsFactoryV6.lenientExpandOpen();
        } else {
            return IndicesOptionsFactoryV7.lenientExpandOpen();
        }
    }

    public static IndicesOptionsInner strictExpandOpen(final ElasticStackVersion version) {
        if (ElasticStackVersion.V6.equals(version)) {
            return IndicesOptionsFactoryV6.strictExpandOpen();
        } else {
            return IndicesOptionsFactoryV7.strictExpandOpen();
        }
    }
}
