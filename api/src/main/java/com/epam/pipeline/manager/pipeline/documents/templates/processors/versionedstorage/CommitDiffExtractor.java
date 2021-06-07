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

package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGrouping;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGroupType;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.stream.Collectors;

public class CommitDiffExtractor implements ReportDataExtractor {

    @Override
    public Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage,
                        final GitDiff diff, final GitDiffReportFilter reportFilter) {
        final GitDiffGroupType groupType = getGroupType(reportFilter);

        GitDiffGrouping.GitDiffGroupingBuilder diffsGroupingBuilder = GitDiffGrouping.builder()
                .type(groupType)
                .includeDiff(reportFilter.isIncludeDiff())
                .archive(reportFilter.isArchive());

        if (reportFilter.isIncludeDiff()) {
            diffsGroupingBuilder
                    .diffGrouping(
                            groupType == GitDiffGroupType.BY_COMMIT
                                    ? diff.getEntries().stream()
                                        .collect(Collectors.groupingBy(e -> e.getCommit().getCommit()))
                                    : diff.getEntries().stream()
                                        .collect(
                                                Collectors.groupingBy(
                                                        e -> e.getDiff().getFromFileName().contains("/dev/null")
                                                                ? e.getDiff().getToFileName()
                                                                : e.getDiff().getFromFileName()
                                                )
                                    )
                    );
        }
        return diffsGroupingBuilder.build();
    }

    private GitDiffGroupType getGroupType(GitDiffReportFilter reportFilter) {
        if (reportFilter.getGroupType() == null) {
            return GitDiffGroupType.BY_COMMIT;
        }
        return reportFilter.getGroupType();
    }

}
