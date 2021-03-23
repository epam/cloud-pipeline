package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
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
    public List<DataStorageTag> insert(final Long root,
                                       final DataStorageObject object,
                                       final Map<String, String> tags) {
        tagDao.delete(root, object);
        return upsert(root, object, tags);
    }

    @Transactional
    public List<DataStorageTag> upsert(final Long root,
                                       final DataStorageObject object,
                                       final Map<String, String> tags) {
        return tagDao.batchUpsert(root, tags.entrySet().stream()
                .map(e -> new DataStorageTag(object, e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
    }

    @Transactional
    public List<DataStorageTag> copy(final Long root,
                                     final DataStorageObject source,
                                     final DataStorageObject destination) {
        final List<DataStorageTag> sourceTags = tagDao.load(root, source);
        tagDao.delete(root, destination);
        return tagDao.batchUpsert(root, sourceTags.stream().map(it -> it.withObject(destination)));
    }

    @Transactional
    public void copyFolder(final Long root, final String oldPath, final String newPath) {
        tagDao.copyFolder(root, oldPath, newPath);
    }

    @Transactional
    public List<DataStorageTag> load(final Long root, final DataStorageObject object) {
        return tagDao.load(root, object);
    }

    @Transactional
    public void delete(final Long root, final DataStorageObject object) {
        tagDao.delete(root, object);
    }

    @Transactional
    public void delete(final Long root, final DataStorageObject object, final Collection<String> keys) {
        tagDao.delete(root, object, new ArrayList<>(keys));
    }

    @Transactional
    public void deleteAllInFolder(final Long root, final String path) {
        tagDao.deleteAllInFolder(root, path);
    }

    @Transactional
    public void deleteAll(final Long root, final String path) {
        tagDao.batchDeleteAll(root, Collections.singletonList(path));   
    }
}
