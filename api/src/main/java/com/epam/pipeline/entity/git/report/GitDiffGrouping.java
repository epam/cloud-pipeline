package com.epam.pipeline.entity.git.report;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class GitDiffGrouping {

    GitDiffGroupType type;
    boolean archive;
    boolean includeDiff;
    Map<String, List<GitParsedDiffEntry>> diffGrouping;

}
