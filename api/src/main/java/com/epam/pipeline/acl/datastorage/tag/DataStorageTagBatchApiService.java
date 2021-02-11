package com.epam.pipeline.acl.datastorage.tag;

import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
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

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public List<DataStorageTag> insert(final Long id,
                                       final DataStorageTagInsertBatchRequest request) {
        return dataStorageTagBatchManager.insert(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public List<DataStorageTag> copy(final Long id,
                                     final DataStorageTagCopyBatchRequest request) {
        return dataStorageTagBatchManager.copy(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<DataStorageTag> load(final Long id,
                                     final DataStorageTagLoadBatchRequest request) {
        return dataStorageTagBatchManager.load(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public void delete(final Long id,
                       final DataStorageTagDeleteBatchRequest request) {
        dataStorageTagBatchManager.delete(id, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public void deleteAll(final Long id,
                          final DataStorageTagDeleteAllBatchRequest request) {
        dataStorageTagBatchManager.deleteAll(id, request);
    }
}
