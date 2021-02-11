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
        tagDao.batchDelete(rootPath.get(), tags.stream().map(DataStorageTag::getObject));
        return tagDao.batchUpsert(rootPath.get(), tags);
    }

    private DataStorageTag tagFrom(final DataStorageTagInsertRequest request) {
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
        tagDao.batchDelete(rootPath.get(), tags.stream().map(DataStorageTag::getObject).distinct());
        return tagDao.batchUpsert(rootPath.get(), tags);
    }

    @Transactional
    public List<DataStorageTag> load(final Long storageId, final DataStorageTagLoadBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Optional<String> rootPath = getRootPath(storageId);
        if (!rootPath.isPresent()) {
            return Collections.emptyList();
        }
        return tagDao.batchLoad(rootPath.get(), request.getRequests().stream()
                .map(DataStorageTagLoadRequest::getPath)
                .collect(Collectors.toList()));
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
        tagDao.batchDelete(rootPath.get(), request.getRequests().stream()
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
        tagDao.batchDeleteAll(rootPath.get(), request.getRequests().stream()
                .map(r -> new DataStorageObject(r.getPath()))
                .map(DataStorageObject::getPath)
                .collect(Collectors.toList()));
    }

    private Optional<String> getRootPath(final Long storageId) {
        return Optional.ofNullable(storageDao.loadDataStorage(storageId))
                .map(AbstractDataStorage::getRoot);
    }
}
