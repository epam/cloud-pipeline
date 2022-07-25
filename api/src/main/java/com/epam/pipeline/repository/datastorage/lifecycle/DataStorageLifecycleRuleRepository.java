package com.epam.pipeline.repository.datastorage.lifecycle;

import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecyclePolicyRuleEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DataStorageLifecycleRuleRepository extends CrudRepository<StorageLifecyclePolicyRuleEntity, Long> {
    List<StorageLifecyclePolicyRuleEntity> findByDatastorageId(Long datastorageId);
    List<StorageLifecyclePolicyRuleEntity> findByPolicyId(Long policyId);

}
