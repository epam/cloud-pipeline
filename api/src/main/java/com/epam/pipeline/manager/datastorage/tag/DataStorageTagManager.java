package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteAllBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteAllRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkLoadRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagUpsertBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DataStorageTagManager {

    private final DataStorageTagDao tagDao;
    private final DataStorageDao storageDao;

    @Transactional
    public List<DataStorageTag> bulkInsert(final Long storageId, final DataStorageTagInsertBulkRequest request) {
        final AbstractDataStorage dataStorage = storageDao.loadDataStorage(storageId);
        if (dataStorage == null) {
            return Collections.emptyList();
        }
        return bulkInsert(Optional.ofNullable(dataStorage.getPath()).orElse(dataStorage.getName()), request);
    }

    @Transactional
    public List<DataStorageTag> bulkCopy(final Long storageId, final DataStorageTagCopyBulkRequest request) {
        final AbstractDataStorage dataStorage = storageDao.loadDataStorage(storageId);
        if (dataStorage == null) {
            return Collections.emptyList();
        }
        return bulkCopy(Optional.ofNullable(dataStorage.getPath()).orElse(dataStorage.getName()), request);
    }
    
    @Transactional
    public List<DataStorageTag> bulkLoad(final Long storageId, final DataStorageTagBulkLoadRequest request) {
        final AbstractDataStorage dataStorage = storageDao.loadDataStorage(storageId);
        if (dataStorage == null) {
            return Collections.emptyList();
        }
        return bulkLoad(Optional.ofNullable(dataStorage.getPath()).orElse(dataStorage.getName()), request);
    }

    @Transactional
    public void bulkDelete(final Long storageId, final DataStorageTagDeleteBulkRequest request) {
        final AbstractDataStorage dataStorage = storageDao.loadDataStorage(storageId);
        if (dataStorage == null) {
            return;
        }
        bulkDelete(Optional.ofNullable(dataStorage.getPath()).orElse(dataStorage.getName()), request);
    }

    @Transactional
    public void bulkDeleteAll(final Long storageId, final DataStorageTagDeleteAllBulkRequest request) {
        final AbstractDataStorage dataStorage = storageDao.loadDataStorage(storageId);
        if (dataStorage == null) {
            return;
        }
        bulkDeleteAll(Optional.ofNullable(dataStorage.getPath()).orElse(dataStorage.getName()), request);
    }

    @Transactional
    public List<DataStorageTag> bulkInsert(final String root, final DataStorageTagInsertBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(tagRequest -> tagFrom(root, tagRequest))
                .collect(Collectors.toList());
        tagDao.bulkDelete(tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(tags);
    }

    private DataStorageTag tagFrom(final String root, final DataStorageTagInsertRequest request) {
        final DataStorageObject object = new DataStorageObject(root, request.getPath(), request.getVersion());
        return new DataStorageTag(object, request.getKey(), request.getValue());
    }

    @Transactional
    public List<DataStorageTag> bulkUpsert(final String root, final DataStorageTagUpsertBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(tagRequest -> tagFrom(root, tagRequest))
                .collect(Collectors.toList());
        return tagDao.bulkUpsert(tags);
    }

    private DataStorageTag tagFrom(final String root, final DataStorageTagUpsertRequest request) {
        final DataStorageObject object = new DataStorageObject(root, request.getPath(), request.getVersion());
        return new DataStorageTag(object, request.getKey(), request.getValue());
    }

    @Transactional
    public List<DataStorageTag> bulkCopy(final String root, final DataStorageTagCopyBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Map<DataStorageTagCopyRequest.DataStorageTagCopyRequestObject, List<DataStorageTag>> sourceTagsMap =
                request.getRequests().stream()
                        .map(DataStorageTagCopyRequest::getSource)
                        .distinct()
                        .collect(Collectors.toMap(Function.identity(),
                                it -> load(new DataStorageObject(root, it.getPath(), it.getVersion()))));
        final List<DataStorageTag> tags = request.getRequests().stream()
                .flatMap(r -> Optional.ofNullable(sourceTagsMap.get(r.getSource()))
                        .map(sourceTags -> sourceTags.stream()
                                .map(it -> it.withObject(new DataStorageObject(root,
                                        r.getDestination().getPath(), r.getDestination().getVersion()))))
                        .orElseGet(Stream::empty))
                .collect(Collectors.toList());
        tagDao.bulkDelete(tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(tags);
    }

    @Transactional
    public List<DataStorageTag> bulkLoad(final String root, final DataStorageTagBulkLoadRequest request) {
        if (CollectionUtils.isEmpty(request.getPaths())) {
            return Collections.emptyList();
        }
        return tagDao.bulkLoad(root, request.getPaths());
    }

    @Transactional
    public void bulkDelete(final String root, final DataStorageTagDeleteBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        tagDao.bulkDelete(request.getRequests().stream()
            .map(r -> new DataStorageObject(root, r.getPath(), r.getVersion())));
    }

    @Transactional
    public void bulkDeleteAll(final String root, final DataStorageTagDeleteAllBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        bulkDeleteAllFiles(root, request.getRequests());
        bulkDeleteAllFolders(root, request.getRequests());
    }

    private void bulkDeleteAllFiles(final String root, final List<DataStorageTagDeleteAllRequest> requests) {
        final List<String> filesToDelete = requests.stream()
                .filter(r -> r.getType() == null
                        || r.getType() == DataStorageTagDeleteAllRequest.DataStorageTagDeleteAllRequestType.FILE)
                .map(r -> new DataStorageObject(root, r.getPath()))
                .map(DataStorageObject::getPath)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(filesToDelete)) {
            return;
        }
        tagDao.bulkDeleteAll(root, filesToDelete);
    }

    private void bulkDeleteAllFolders(final String root, final List<DataStorageTagDeleteAllRequest> requests) {
        final List<String> foldersToDelete = requests.stream()
                .filter(r -> r.getType() != null
                        && r.getType() == DataStorageTagDeleteAllRequest.DataStorageTagDeleteAllRequestType.FOLDER)
                .map(DataStorageTagDeleteAllRequest::getPath)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(foldersToDelete)) {
            return;
        }
        foldersToDelete.forEach(path -> tagDao.deleteAllInFolder(root, path));
    }

    @Transactional
    public DataStorageTag upsert(final DataStorageTag tag) {
        return tagDao.upsert(tag);
    }

    @Transactional
    public List<DataStorageTag> upsert(final Stream<DataStorageTag> tags) {
        return tagDao.bulkUpsert(tags);
    }

    @Transactional
    public List<DataStorageTag> load(final DataStorageObject object) {
        return tagDao.load(object);
    }

    @Transactional
    public void delete(final DataStorageObject object) {
        tagDao.delete(object);
    }

    @Transactional
    public void delete(final DataStorageObject object, final List<String> keys) {
        tagDao.delete(object, keys);
    }

    @Transactional
    public void delete(final DataStorageObject object, final Collection<String> keys) {
        tagDao.delete(object, new ArrayList<>(keys));
    }
}
