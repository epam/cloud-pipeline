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

package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs batch operations with storage objects to insert, update, list and delete tags for these objects.
 *
 * @implNote All these operations expects from client to provide full paths of the storage object
 *           (full path is a path from datastorage_root of storage and not from storage itself) with in BatchRequests
 *           for example:
 *           @see DataStorageTagBatchManager#upsert(Long, DataStorageTagUpsertBatchRequest) and
 *           @see DataStorageTagDao to get more information on actual logic
 *  TODO: Maybe it is good idea to expand API to allow to specify flag 'relative' on BatchRequest level,
 *  TODO: to be able to automatically performs such resolving of paths on server side rather that client side
 * */
@Service
@RequiredArgsConstructor
public class DataStorageTagBatchManager {

    private final DataStorageTagDao tagDao;
    private final DataStorageDao storageDao;

    @Transactional
    public List<DataStorageTag> insert(final Long storageId, final DataStorageTagInsertBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(this::tagFrom)
                .collect(Collectors.toList());
        tagDao.batchDelete(root.get(), tags.stream().map(DataStorageTag::getObject));
        return tagDao.batchUpsert(root.get(), tags);
    }

    private DataStorageTag tagFrom(final DataStorageTagInsertRequest request) {
        final DataStorageObject object = new DataStorageObject(request.getPath(), request.getVersion());
        return new DataStorageTag(object, request.getKey(), request.getValue());
    }

    @Transactional
    public List<DataStorageTag> upsert(final Long storageId, final DataStorageTagUpsertBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(this::tagFrom)
                .collect(Collectors.toList());
        return tagDao.batchUpsert(root.get(), tags);
    }

    private DataStorageTag tagFrom(final DataStorageTagUpsertRequest request) {
        final DataStorageObject object = new DataStorageObject(request.getPath(), request.getVersion());
        return new DataStorageTag(object, request.getKey(), request.getValue());
    }

    @Transactional
    public List<DataStorageTag> copy(final Long storageId, final DataStorageTagCopyBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final Map<DataStorageTagCopyRequest.DataStorageTagCopyRequestObject, List<DataStorageTag>> sourceTagsMap =
                request.getRequests().stream()
                        .map(DataStorageTagCopyRequest::getSource)
                        .distinct()
                        .collect(Collectors.toMap(Function.identity(),
                            it -> tagDao.load(root.get(), new DataStorageObject(it.getPath(), it.getVersion()))));
        final List<DataStorageTag> tags = request.getRequests().stream()
                .flatMap(r -> Optional.ofNullable(sourceTagsMap.get(r.getSource()))
                        .map(sourceTags -> sourceTags.stream()
                                .map(it -> it.withObject(new DataStorageObject(r.getDestination().getPath(), 
                                        r.getDestination().getVersion()))))
                        .orElseGet(Stream::empty))
                .collect(Collectors.toList());
        tagDao.batchDelete(root.get(), tags.stream().map(DataStorageTag::getObject).distinct());
        return tagDao.batchUpsert(root.get(), tags);
    }

    @Transactional
    public List<DataStorageTag> load(final Long storageId, final DataStorageTagLoadBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        return tagDao.batchLoad(root.get(), request.getRequests().stream()
                .map(DataStorageTagLoadRequest::getPath)
                .collect(Collectors.toList()));
    }

    @Transactional
    public void delete(final Long storageId, final DataStorageTagDeleteBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return;
        }
        tagDao.batchDelete(root.get(), request.getRequests().stream()
            .map(r -> new DataStorageObject(r.getPath(), r.getVersion())));
    }

    @Transactional
    public void deleteAll(final Long storageId, final DataStorageTagDeleteAllBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        final Optional<Long> rootPath = getRoot(storageId);
        if (!rootPath.isPresent()) {
            return;
        }
        tagDao.batchDeleteAll(rootPath.get(), request.getRequests().stream()
                .map(r -> new DataStorageObject(r.getPath()))
                .map(DataStorageObject::getPath)
                .collect(Collectors.toList()));
    }

    private Optional<Long> getRoot(final Long storageId) {
        return Optional.ofNullable(storageDao.loadDataStorage(storageId))
                .map(AbstractDataStorage::getRootId);
    }
}
