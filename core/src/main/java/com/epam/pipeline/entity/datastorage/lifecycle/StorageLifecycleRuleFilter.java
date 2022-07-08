package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Value
@Builder
public class StorageLifecycleRuleFilter {
    List<String> prefixes;
    List<Pair<String, String>> tags;
}
