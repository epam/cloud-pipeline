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

package com.epam.pipeline.dto.datastorage.lifecycle.restore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageRestoreActionSearchFilter {
    private Long datastorageId;
    private StorageRestorePath path;

    /**
     * @see SearchType, for more details
     * */
    private SearchType searchType;

    /**
     * If true, will filter method will return only one (and the latest, will determinate it by started date)
     * restore action for path (if present).
     * */
    private Boolean isLatest;

    private List<StorageRestoreStatus> statuses;

    /**
     * SEARCH_PARENT - will search for all action that has path type == FOLDER and include path from filter,
     *                 also will include actions with the same path
     * SEARCH_CHILD - if path from filter is a folder, will search for all action under this path
     *                (one hierarchy level only), also will include actions with the same path
     * SEARCH_CHILD_RECURSIVELY - if path from filter is a folder, will search for all action under this path
     *                            (all hierarchy levels), also will include actions with the same path
     * */
    public enum SearchType {
        SEARCH_PARENT, SEARCH_CHILD, SEARCH_CHILD_RECURSIVELY
    }
}
