/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.metadata;

/**
 * Provides tag types for common instance tags that can be applied via
 * {@link com.epam.pipeline.manager.preference.SystemPreferences#CLUSTER_INSTANCE_TAGS}
 *
 * Supported values:
 * - tool - Docker image of a tool used for a run
 * - run_id - Integer ID of a run
 * - owner - Username of a run owner
 */
public enum CommonInstanceTagsType {
    tool, run_id, owner
}
