package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StorageLifecycleRule {

    String id;
    StorageLifecycleRuleFilter filter;
    List<StorageLifecycleRuleTransition> transitions;
    Integer expirationAfterDays;

}
