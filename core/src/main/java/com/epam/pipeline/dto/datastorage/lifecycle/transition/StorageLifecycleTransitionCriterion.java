/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dto.datastorage.lifecycle.transition;

import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * Describes strategy to decide when files defined in {@link StorageLifecycleRule} should be transitioned.
 * */
@Value
@Builder(toBuilder = true)
@EqualsAndHashCode
public class StorageLifecycleTransitionCriterion {

    StorageLifecycleTransitionCriterionType type;
    String value;

    public String toDescriptionString() {
        return type.name() + (StringUtils.isNotBlank(value) ? "='" + value + "'" : "");
    }

    public enum StorageLifecycleTransitionCriterionType {

        /**
         * Means that transition conditions from {@link StorageLifecycleRuleTransition} are applied to matching
         * files itself.
         * */
        DEFAULT,

        /**
         * Means that transition conditions from {@link StorageLifecycleRuleTransition} are applied to matching
         * files from {@code value} field.

         * For this type of criterion {@code value} should be provided and should be a glob, to match files.
         * NOTE: glob is resolved from root directory to which rule is applied.
         * */
        MATCHING_FILES
    }
}
