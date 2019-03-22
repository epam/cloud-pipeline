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

package com.epam.pipeline.entity.configuration;

/**
 * Class for common utility methods connected with {@link RunConfiguration}
 */
public final class RunConfigurationUtils {

    /**
     * Calculates node count from {@link PipelineConfiguration#nodeCount} gracefully: if this value is not specified
     * returns {@code basicValue}.
     * @param nodeCount current node count
     * @param basicValue initial node count
     * @return node count
     */
    public static Integer getNodeCount(Integer nodeCount, int basicValue) {
        return nodeCount == null ? basicValue : nodeCount + basicValue;
    }

    private RunConfigurationUtils() {
        //no-op
    }
}
