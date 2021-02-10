package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteAllRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagUpsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return Collections.emptyList();
        }

        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(this::tagFrom)
                .collect(Collectors.toList());
        tagDao.bulkDelete(rootPath.get(), tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(rootPath.get(), tags);
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
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return Collections.emptyList();
        }
        
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(this::tagFrom)
                .collect(Collectors.toList());
        return tagDao.bulkUpsert(rootPath.get(), tags);
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
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return Collections.emptyList();
        }
        
        final Map<DataStorageTagCopyRequest.DataStorageTagCopyRequestObject, List<DataStorageTag>> sourceTagsMap =
                request.getRequests().stream()
                        .map(DataStorageTagCopyRequest::getSource)
                        .distinct()
                        .collect(Collectors.toMap(Function.identity(),
                                it -> tagDao.load(rootPath.get(), new DataStorageObject(it.getPath(), it.getVersion()))));
        final List<DataStorageTag> tags = request.getRequests().stream()
                .flatMap(r -> Optional.ofNullable(sourceTagsMap.get(r.getSource()))
                        .map(sourceTags -> sourceTags.stream()
                                .map(it -> it.withObject(new DataStorageObject(r.getDestination().getPath(), 
                                        r.getDestination().getVersion()))))
                        .orElseGet(Stream::empty))
                .collect(Collectors.toList());
        tagDao.bulkDelete(rootPath.get(), tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(rootPath.get(), tags);
    }

    @Transactional
    public List<DataStorageTag> load(final Long storageId, final DataStorageTagLoadBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getPaths())) {
            return Collections.emptyList();
        }
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return Collections.emptyList();
        }
        return tagDao.bulkLoad(rootPath.get(), request.getPaths());
    }

    @Transactional
    public void delete(final Long storageId, final DataStorageTagDeleteBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return;
        }
        
        tagDao.bulkDelete(rootPath.get(), request.getRequests().stream()
            .map(r -> new DataStorageObject(r.getPath(), r.getVersion())));
    }

    @Transactional
    public void deleteAll(final Long storageId, final DataStorageTagDeleteAllBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return;
        }
        
        deleteAllFiles(rootPath.get(), request.getRequests());
        deleteAllFolders(rootPath.get(), request.getRequests());
    }

    private void deleteAllFiles(final String rootPath, final List<DataStorageTagDeleteAllRequest> requests) {
        final List<String> filesToDelete = requests.stream()
                .filter(r -> r.getType() == null
                        || r.getType() == DataStorageTagDeleteAllRequest.DataStorageTagDeleteAllRequestType.FILE)
                .map(r -> new DataStorageObject(r.getPath()))
                .map(DataStorageObject::getPath)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(filesToDelete)) {
            return;
        }
        tagDao.bulkDeleteAll(rootPath, filesToDelete);
    }

    private void deleteAllFolders(final String rootPath, final List<DataStorageTagDeleteAllRequest> requests) {
        final List<String> foldersToDelete = requests.stream()
                .filter(r -> r.getType() != null
                        && r.getType() == DataStorageTagDeleteAllRequest.DataStorageTagDeleteAllRequestType.FOLDER)
                .map(DataStorageTagDeleteAllRequest::getPath)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(foldersToDelete)) {
            return;
        }
        foldersToDelete.forEach(path -> tagDao.deleteAllInFolder(rootPath, path));
    }

    private Optional<String> getRootPath(final Long storageId) {
        return Optional.ofNullable(storageDao.loadDataStorage(storageId))
                .map(AbstractDataStorage::getRoot);
    }
}
