package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.CommitDiffsGrouping;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommitDiffExtractor implements ReportDataExtractor {

    private final static Pattern PATTERN = Pattern.compile("\\{commit_diffs:?(.*)}");

    @Override
    public Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage, final GitDiff diff) {
        Matcher matcher = PATTERN.matcher(xwpfParagraph.getText());
        CommitDiffsGrouping.GroupType groupType = getGroupType(matcher);

        return CommitDiffsGrouping.builder()
                .type(groupType)
                .diffGrouping(
                        groupType == CommitDiffsGrouping.GroupType.BY_COMMIT
                        ? diff.getEntries().stream().collect(Collectors.groupingBy(e -> e.getCommit().getCommit()))
                        : diff.getEntries().stream().collect(Collectors.groupingBy(e -> e.getDiff().getToFileName()))
                ).build();
    }

    private CommitDiffsGrouping.GroupType getGroupType(Matcher matcher) {
        CommitDiffsGrouping.GroupType groupType = null;
        if (groupType == null) {
            groupType = CommitDiffsGrouping.GroupType.BY_COMMIT;
        }
        return groupType;
    }

}
