package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteAllBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkLoadRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertBulkRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DataStorageTagManager {

    private final DataStorageTagDao tagDao;

    public List<DataStorageTag> bulkInsert(final Long storageId, final DataStorageTagInsertBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final List<DataStorageTag> tags = request.getRequests().stream()
                .map(tagRequest -> asTag(storageId, tagRequest))
                .collect(Collectors.toList());
        tagDao.bulkDelete(tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(tags);
    }

    private DataStorageTag asTag(final Long storageId, final DataStorageTagInsertRequest request) {
        final DataStorageObject object = new DataStorageObject(storageId, request.getPath(), request.getVersion());
        return new DataStorageTag(object, request.getKey(), request.getValue());
    }

    public List<DataStorageTag> bulkCopy(final Long storageId, final DataStorageTagCopyBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        final Map<DataStorageTagCopyRequest.DataStorageTagCopyRequestObject, List<DataStorageTag>> sourceTags = request.getRequests().stream()
                .map(DataStorageTagCopyRequest::getSource)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), it -> load(new DataStorageObject(storageId, it.getPath(), it.getVersion()))));
        final List<DataStorageTag> tags = request.getRequests().stream()
                .flatMap(r -> sourceTags.get(r.getSource()).stream()
                        .map(it -> it.withObject(new DataStorageObject(storageId, r.getDestination().getPath(), r.getDestination().getVersion()))))
                .collect(Collectors.toList());
        tagDao.bulkDelete(tags.stream().map(DataStorageTag::getObject));
        return tagDao.bulkUpsert(tags);
    }

    public List<DataStorageTag> bulkLoad(final Long storageId, final DataStorageTagBulkLoadRequest request) {
        if (CollectionUtils.isEmpty(request.getPaths())) {
            return Collections.emptyList();
        }
        return tagDao.bulkLoad(storageId, request.getPaths());
    }

    public void bulkDelete(final Long storageId, final DataStorageTagDeleteBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        tagDao.bulkDelete(request.getRequests().stream()
            .map(r -> new DataStorageObject(storageId, r.getPath(), r.getVersion())));
    }

    public void bulkDeleteAll(final Long storageId, final DataStorageTagDeleteAllBulkRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        tagDao.bulkDeleteAll(request.getRequests().stream()
            .map(r -> new DataStorageObject(storageId, r.getPath())));
    }

    public DataStorageTag upsert(final DataStorageTag tag) {
        return tagDao.upsert(tag);
    }

    public List<DataStorageTag> upsert(final Stream<DataStorageTag> tags) {
        return tagDao.bulkUpsert(tags);
    }

    public Map<String, String> upsertFromMap(final DataStorageObject object, final Map<String, String> tags) {
        return asMap(tagDao.bulkUpsert(tags.entrySet().stream()
                .map(entry -> new DataStorageTag(object, entry.getKey(), entry.getValue()))));
    }

    public List<DataStorageTag> load(final DataStorageObject object) {
        return tagDao.load(object);
    }
    
    public Map<String, String> loadAsMap(final DataStorageObject object) {
        return asMap(tagDao.load(object));
    }

    public void delete(final DataStorageObject object) {
        tagDao.delete(object);
    }

    public void delete(final DataStorageObject object, final List<String> keys) {
        tagDao.delete(object, keys);
    }

    public void delete(final DataStorageObject object, final Collection<String> keys) {
        tagDao.delete(object, new ArrayList<>(keys));
    }

    private Map<String, String> asMap(final List<DataStorageTag> tags) {
        return tags.stream().collect(Collectors.toMap(DataStorageTag::getKey, DataStorageTag::getValue));
    }
}
