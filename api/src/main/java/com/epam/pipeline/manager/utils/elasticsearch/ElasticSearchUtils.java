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

package com.epam.pipeline.manager.utils.elasticsearch;

public final class ElasticSearchUtils {

    public static final String ES_WILDCARD = "*";
    public static final String ES_DOC_FIELDS_SEPARATOR = ".";
    public static final String ES_DOC_AGGS_SEPARATOR = ">";
    public static final String ES_ELEMENTS_SEPARATOR = ",";

    private ElasticSearchUtils() {}

    public static String fieldsPath(final String... paths) {
        return bucketsPath(ES_DOC_FIELDS_SEPARATOR, paths);
    }

    public static String bucketsPath(final String separator, final String[] paths) {
        return String.join(separator, paths);
    }

}
