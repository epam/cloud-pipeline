package com.epam.pipeline.repository.datastorage.lifecycle;

import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecyclePolicyEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DataStorageLifecyclePolicyRepository extends CrudRepository<StorageLifecyclePolicyEntity, Long> {
    List<StorageLifecyclePolicyEntity> findByDatastorageId(Long datastorageId);
}
