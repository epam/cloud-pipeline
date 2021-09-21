/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor.extractor;

import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.git.report.GitDiffGrouping;
import com.epam.pipeline.entity.git.report.GitDiffGroupType;
import com.epam.pipeline.manager.utils.DiffUtils;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommitDiffExtractor implements ReportDataExtractor<GitDiffGrouping> {

    @Override
    public GitDiffGrouping extract(final XWPFParagraph xwpfParagraph, final Pipeline storage,
                        final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        final GitDiffGroupType groupType = getGroupType(reportFilter);
        return GitDiffGrouping.builder()
                .type(groupType)
                .includeDiff(reportFilter.isIncludeDiff())
                .diffGrouping(reportFilter.isIncludeDiff() ? constructDiffGrouping(diff, groupType) : null)
                .archive(reportFilter.isArchive()).build();
    }

    private Map<String, List<GitParsedDiffEntry>> constructDiffGrouping(final GitParsedDiff diff,
                                                                        final GitDiffGroupType groupType) {
        return groupType == GitDiffGroupType.BY_COMMIT
            ? diff.getEntries().stream()
                .sorted(Comparator.comparing(e -> e.getCommit().getCommitterDate()))
                .collect(Collectors.groupingBy(e -> e.getCommit().getCommit()))
            : diff.getEntries()
                .stream()
                .collect(Collectors.groupingBy(e -> DiffUtils.getChangedFileName(e.getDiff())));
    }

    private GitDiffGroupType getGroupType(GitDiffReportFilter reportFilter) {
        if (reportFilter.getGroupType() == null) {
            return GitDiffGroupType.BY_COMMIT;
        }
        return reportFilter.getGroupType();
    }

}
