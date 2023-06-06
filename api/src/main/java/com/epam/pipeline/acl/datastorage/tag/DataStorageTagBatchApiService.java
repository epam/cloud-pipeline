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

package com.epam.pipeline.acl.datastorage.tag;

import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertBatchRequest;
import com.epam.pipeline.manager.datastorage.tag.DataStorageTagBatchManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DataStorageTagBatchApiService {

    private final DataStorageTagBatchManager dataStorageTagBatchManager;

    @PreAuthorize(AclExpressions.STORAGE_ID_TAGS_REQUEST_WRITE)
    public List<DataStorageTag> insert(final Long id, final DataStorageTagInsertBatchRequest request) {
        return dataStorageTagBatchManager.insert(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_TAGS_REQUEST_WRITE)
    public List<DataStorageTag> upsert(final Long id, final DataStorageTagUpsertBatchRequest request) {
        return dataStorageTagBatchManager.upsert(id, request);

    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public List<DataStorageTag> copy(final Long id, final DataStorageTagCopyBatchRequest request) {
        return dataStorageTagBatchManager.copy(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<DataStorageTag> load(final Long id, final DataStorageTagLoadBatchRequest request) {
        return dataStorageTagBatchManager.load(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public void delete(final Long id, final DataStorageTagDeleteBatchRequest request) {
        dataStorageTagBatchManager.delete(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public void deleteAll(final Long id, final DataStorageTagDeleteAllBatchRequest request) {
        dataStorageTagBatchManager.deleteAll(id, request);
    }
}
