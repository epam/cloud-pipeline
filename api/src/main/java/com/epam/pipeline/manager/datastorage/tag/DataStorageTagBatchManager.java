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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertRequest;
import com.epam.pipeline.manager.datastorage.permissions.StoragePathPermissionsService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStorageTagBatchManager {

    private final DataStorageTagDao tagDao;
    private final DataStorageDao storageDao;
    private final AuthManager authManager;
    private final StoragePermissionManager storagePermissionManager;
    private final StoragePathPermissionsService storagePathPermissionsService;

    @Transactional
    public List<DataStorageTag> insert(final Long storageId, final DataStorageTagInsertBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<String> allowedPaths = getAllowedPaths(storageId, request.requests(),
                DataStorageTagInsertRequest::path, AclPermission.WRITE.getMask());
        if (Objects.nonNull(allowedPaths) && CollectionUtils.isEmpty(allowedPaths)) {
            log.debug("No allowed path found for storage '{}' to insert tags.", storageId);
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.requests().stream()
                .filter(insertRequest -> isPathAllowed(insertRequest.path(), allowedPaths))
                .map(this::tagFrom)
                .collect(Collectors.toList());
        tagDao.batchDelete(root.get(), tags.stream().map(DataStorageTag::object));
        return tagDao.batchUpsert(root.get(), tags);
    }

    private DataStorageTag tagFrom(final DataStorageTagInsertRequest request) {
        final DataStorageObject object = new DataStorageObject(request.path(), request.version());
        return new DataStorageTag(object, request.key(), request.value());
    }

    @Transactional
    public List<DataStorageTag> upsert(final Long storageId, final DataStorageTagUpsertBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<String> allowedPaths = getAllowedPaths(storageId, request.requests(),
                DataStorageTagUpsertRequest::path, AclPermission.WRITE.getMask());
        if (Objects.nonNull(allowedPaths) && CollectionUtils.isEmpty(allowedPaths)) {
            log.debug("No allowed path found for storage '{}' to upsert tags.", storageId);
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.requests().stream()
                .filter(upsertRequest -> isPathAllowed(upsertRequest.path(), allowedPaths))
                .map(this::tagFrom)
                .collect(Collectors.toList());
        return tagDao.batchUpsert(root.get(), tags);
    }

    private DataStorageTag tagFrom(final DataStorageTagUpsertRequest request) {
        final DataStorageObject object = new DataStorageObject(request.path(), request.version());
        return new DataStorageTag(object, request.key(), request.value());
    }

    @Transactional
    public List<DataStorageTag> copy(final Long storageId, final DataStorageTagCopyBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<String> readAllowedPaths = getAllowedPaths(storageId, request.requests().stream()
                        .map(DataStorageTagCopyRequest::source).collect(Collectors.toList()),
                DataStorageTagCopyRequest.DataStorageTagCopyRequestObject::path, AclPermission.READ.getMask());
        if (Objects.nonNull(readAllowedPaths) && CollectionUtils.isEmpty(readAllowedPaths)) {
            log.debug("No allowed path found for storage '{}' to load tags for copy.", storageId);
            return Collections.emptyList();
        }
        final Map<DataStorageTagCopyRequest.DataStorageTagCopyRequestObject, List<DataStorageTag>> sourceTagsMap =
                request.requests().stream()
                        .map(DataStorageTagCopyRequest::source)
                        .distinct()
                        .filter(o -> isPathAllowed(o.path(), readAllowedPaths))
                        .collect(Collectors.toMap(Function.identity(),
                            it -> tagDao.load(root.get(), new DataStorageObject(it.path(), it.version()))));
        final List<String> writeAllowedPaths = getAllowedPaths(storageId, request.requests().stream()
                        .map(DataStorageTagCopyRequest::destination).collect(Collectors.toList()),
                DataStorageTagCopyRequest.DataStorageTagCopyRequestObject::path, AclPermission.WRITE.getMask());
        if (Objects.nonNull(writeAllowedPaths) && CollectionUtils.isEmpty(writeAllowedPaths)) {
            log.debug("No allowed path found for storage '{}' to copy tags.", storageId);
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.requests().stream()
                .filter(r -> isPathAllowed(r.destination().path(), writeAllowedPaths))
                .flatMap(r -> Optional.ofNullable(sourceTagsMap.get(r.source()))
                        .map(sourceTags -> sourceTags.stream()
                                .map(it -> it.withObject(new DataStorageObject(r.destination().path(),
                                        r.destination().version()))))
                        .orElseGet(Stream::empty))
                .collect(Collectors.toList());
        tagDao.batchDelete(root.get(), tags.stream().map(DataStorageTag::object).distinct());
        return tagDao.batchUpsert(root.get(), tags);
    }

    @Transactional
    public List<DataStorageTag> load(final Long storageId, final DataStorageTagLoadBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return Collections.emptyList();
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return Collections.emptyList();
        }
        final List<String> allowedPaths = getAllowedPaths(storageId, request.requests(),
                DataStorageTagLoadRequest::path, AclPermission.READ.getMask());
        if (Objects.nonNull(allowedPaths) && CollectionUtils.isEmpty(allowedPaths)) {
            log.debug("No allowed path found for storage '{}' to load tags.", storageId);
            return Collections.emptyList();
        }
        return tagDao.batchLoad(root.get(), request.requests().stream()
                .map(DataStorageTagLoadRequest::path)
                .filter(path -> isPathAllowed(path, allowedPaths))
                .collect(Collectors.toList()));
    }

    @Transactional
    public void delete(final Long storageId, final DataStorageTagDeleteBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return;
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return;
        }
        final List<String> allowedPaths = getAllowedPaths(storageId, request.requests(),
                DataStorageTagDeleteRequest::path, AclPermission.WRITE.getMask());
        if (Objects.nonNull(allowedPaths) && CollectionUtils.isEmpty(allowedPaths)) {
            log.debug("No allowed path found for storage '{}' to delete tags.", storageId);
            return;
        }
        tagDao.batchDelete(root.get(), request.requests().stream()
                .filter(r -> isPathAllowed(r.path(), allowedPaths))
                .map(r -> new DataStorageObject(r.path(), r.version())));
    }

    @Transactional
    public void deleteAll(final Long storageId, final DataStorageTagDeleteAllBatchRequest request) {
        if (CollectionUtils.isEmpty(request.requests())) {
            return;
        }
        final Optional<Long> root = getRoot(storageId);
        if (!root.isPresent()) {
            return;
        }
        final List<String> allowedPaths = getAllowedPaths(storageId, request.requests(),
                DataStorageTagDeleteAllRequest::path, AclPermission.WRITE.getMask());
        if (Objects.nonNull(allowedPaths) && CollectionUtils.isEmpty(allowedPaths)) {
            log.debug("No allowed path found for storage '{}' to delete all tags.", storageId);
            return;
        }
        tagDao.batchDeleteAll(root.get(), request.requests().stream()
                .filter(r -> isPathAllowed(r.path(), allowedPaths))
                .map(r -> new DataStorageObject(r.path()))
                .map(DataStorageObject::path)
                .collect(Collectors.toList()));
    }

    private Optional<Long> getRoot(final Long storageId) {
        return Optional.ofNullable(storageDao.loadDataStorage(storageId))
                .map(AbstractDataStorage::getRootId);
    }

    private <T> List<String> getAllowedPaths(final Long storageId, final List<T> requests,
                                             final Function<T, String> getPathFunction,
                                             final int mask) {
        if (storagePermissionManager.isStorageAdmin()) {
            return null;
        }
        final AbstractDataStorage storage = storageDao.loadDataStorage(storageId);
        if (Objects.isNull(storage)) {
            log.debug("Storage '{}' was not found.", storageId);
            return Collections.emptyList();
        }
        if (!(storage.isPathPermissionsEnabled() && DataStorageType.S3.equals(storage.getType())
                && !authManager.getAuthorizedUser().equalsIgnoreCase(storage.getOwner()))) {
            return null;
        }
        return storagePathPermissionsService.filterFiles(storageId, requests.stream()
                .map(getPathFunction)
                .distinct()
                .collect(Collectors.toList()), mask);
    }

    private boolean isPathAllowed(final String targetPath, final List<String> allowedPaths) {
        return Objects.isNull(allowedPaths) || allowedPaths.contains(targetPath);
    }
}
