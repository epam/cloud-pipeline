package com.epam.pipeline.manager.pipeline.documents.templates.structure;

import com.epam.pipeline.entity.git.GitDiffEntry;
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
    Map<String, List<GitDiffEntry>> diffGrouping;

}
