package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StorageLifecyclePolicy {
    List<StorageLifecycleRule> rules;
}
