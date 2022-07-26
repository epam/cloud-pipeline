package com.epam.pipeline.repository.datastorage.lifecycle;

import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleTemplateEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DataStorageLifecycleRuleTemplateRepository extends CrudRepository<StorageLifecycleRuleTemplateEntity, Long> {
    List<StorageLifecycleRuleTemplateEntity> findByDatastorageId(Long datastorageId);
}
