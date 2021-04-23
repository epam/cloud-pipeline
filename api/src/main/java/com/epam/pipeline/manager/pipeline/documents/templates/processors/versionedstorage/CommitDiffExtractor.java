package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.CommitDiffsGrouping;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.stream.Collectors;

public class CommitDiffExtractor implements ReportDataExtractor {

    @Override
    public Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage,
                        final GitDiff diff, final GitDiffReportFilter reportFilter) {
        final CommitDiffsGrouping.GroupType groupType = getGroupType(reportFilter);

        CommitDiffsGrouping.CommitDiffsGroupingBuilder diffsGroupingBuilder = CommitDiffsGrouping.builder()
                .type(groupType)
                .includeDiff(reportFilter.isIncludeDiff())
                .archive(reportFilter.isArchive());

        if (reportFilter.isIncludeDiff()) {
            diffsGroupingBuilder
                    .diffGrouping(
                            groupType == CommitDiffsGrouping.GroupType.BY_COMMIT
                                    ? diff.getEntries().stream()
                                        .collect(Collectors.groupingBy(e -> e.getCommit().getCommit()))
                                    : diff.getEntries().stream()
                                        .collect(Collectors.groupingBy(e -> e.getDiff().getToFileName()))
                    );
        }
        return diffsGroupingBuilder.build();
    }

    private CommitDiffsGrouping.GroupType getGroupType(GitDiffReportFilter reportFilter) {
        if (reportFilter.getGroupType() == null) {
            return CommitDiffsGrouping.GroupType.BY_COMMIT;
        }
        return reportFilter.getGroupType();
    }

}
