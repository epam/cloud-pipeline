package com.epam.pipeline.repository.datastorage.lifecycle;

import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DataStorageLifecycleRuleRepository extends CrudRepository<StorageLifecycleRuleEntity, Long> {
    List<StorageLifecycleRuleEntity> findByDatastorageId(Long datastorageId);
    List<StorageLifecycleRuleEntity> findByTemplateId(Long templateId);

}
