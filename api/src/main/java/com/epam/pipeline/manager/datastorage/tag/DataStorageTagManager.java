package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataStorageTagManager {

    private final DataStorageTagDao tagDao;

    @Transactional
    public List<DataStorageTag> insert(final String rootPath,
                                       final DataStorageObject object,
                                       final Map<String, String> tags) {
        tagDao.delete(rootPath, object);
        return upsert(rootPath, object, tags);
    }

    @Transactional
    public List<DataStorageTag> upsert(final String rootPath,
                                       final DataStorageObject object,
                                       final Map<String, String> tags) {
        return tagDao.batchUpsert(rootPath, tags.entrySet().stream()
                .map(e -> new DataStorageTag(object, e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
    }

    @Transactional
    public List<DataStorageTag> copy(final String rootPath,
                                     final DataStorageObject source,
                                     final DataStorageObject destination) {
        final List<DataStorageTag> sourceTags = tagDao.load(rootPath, source);
        tagDao.delete(rootPath, destination);
        return tagDao.batchUpsert(rootPath, sourceTags.stream().map(it -> it.withObject(destination)));
    }

    @Transactional
    public void copyFolder(final String rootPath, final String oldPath, final String newPath) {
        tagDao.copyFolder(rootPath, oldPath, newPath);
    }

    @Transactional
    public List<DataStorageTag> load(final String rootPath, final DataStorageObject object) {
        return tagDao.load(rootPath, object);
    }

    @Transactional
    public void delete(final String rootPath, final DataStorageObject object) {
        tagDao.delete(rootPath, object);
    }

    @Transactional
    public void delete(final String rootPath, final DataStorageObject object, final Collection<String> keys) {
        tagDao.delete(rootPath, object, new ArrayList<>(keys));
    }

    @Transactional
    public void deleteAllInFolder(final String rootPath, final String path) {
        tagDao.deleteAllInFolder(rootPath, path);
    }

    @Transactional
    public void deleteAll(final String rootPath, final String path) {
        tagDao.batchDeleteAll(rootPath, Collections.singletonList(path));   
    }
}
