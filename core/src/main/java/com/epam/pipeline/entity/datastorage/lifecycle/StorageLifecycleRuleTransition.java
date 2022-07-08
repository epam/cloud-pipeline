package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StorageLifecycleRuleTransition {
    Integer transitionAfterDays;
    String StorageClass;
}
