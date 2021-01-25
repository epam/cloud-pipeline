package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkDeleteRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkLoadRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DataStorageTagManager {

    private final DataStorageTagDao tagDao;

    public List<DataStorageTag> bulkUpsert(final Long storageId, final DataStorageTagBulkUpsertRequest request) {
        return tagDao.upsert(request.getPathTags().entrySet().stream()
                .flatMap(pathEntry -> {
                    final DataStorageObject object = new DataStorageObject(storageId, pathEntry.getKey());
                    return pathEntry.getValue().entrySet().stream()
                            .map(tagEntry -> new DataStorageTag(object, tagEntry.getKey(), tagEntry.getValue()));
                }));
    }

    public List<DataStorageTag> bulkLoad(final Long storageId, final DataStorageTagBulkLoadRequest request) {
        return tagDao.load(storageId, request.getPaths());
    }

    public void bulkDelete(final Long storageId, final DataStorageTagBulkDeleteRequest request) {
        tagDao.delete(storageId, request.getPaths());
    }

    public DataStorageTag upsert(final DataStorageTag tag) {
        return tagDao.upsert(tag);
    }

    public List<DataStorageTag> upsert(final Stream<DataStorageTag> tags) {
        return tagDao.upsert(tags);
    }

    public Map<String, String> upsertFromMap(final DataStorageObject object, final Map<String, String> tags) {
        return asMap(tagDao.upsert(tags.entrySet().stream()
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
