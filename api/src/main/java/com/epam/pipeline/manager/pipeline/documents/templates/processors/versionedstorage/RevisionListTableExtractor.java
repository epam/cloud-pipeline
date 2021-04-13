package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.TableRow;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RevisionListTableExtractor implements ReportDataExtractor{

    private final static Pattern PATTERN = Pattern.compile("\\{revision_history_table:?(.*)}");

    @Override
    public Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage, final GitDiff diff) {
        Matcher matcher = PATTERN.matcher(xwpfParagraph.getText());
        final Map<RevisionHistoryTableColumn, String> tableColumns;
        if(matcher.matches()) {
            final String tableStructure = matcher.group(1);
            tableColumns = parseTableStructure(tableStructure);
        } else {
            tableColumns = Arrays.stream(RevisionHistoryTableColumn.values()).collect(Collectors.toMap(c -> c, c -> c.value));
        }
        final Table result = new Table();
        result.setContainsHeaderRow(true);
        for (String value : tableColumns.values()) {
            result.addColumn(value);
        }

        diff.getEntries().stream()
                .map(gitDiffEntry -> {
                    final String file = gitDiffEntry.getDiff().getToFileName().equals("/dev/null")
                            ? gitDiffEntry.getDiff().getFromFileName()
                            : gitDiffEntry.getDiff().getToFileName();
                    return new Pair<>(file, gitDiffEntry.getCommit());
                }).forEachOrdered(fileAndCommit -> {
                    TableRow row = result.addRow(fileAndCommit.getKey() + fileAndCommit.getValue().getCommit());
                    tableColumns.forEach((e, v) -> result.setData(row.getName(), v, getData(fileAndCommit, e)));
                });
        return result;
    }

    private String getData(Pair<String, GitReaderRepositoryCommit> fileAndCommit, RevisionHistoryTableColumn e) {
        switch (e) {
            case PATH:
                return fileAndCommit.getKey();
            case AUTHOR:
                return fileAndCommit.getValue().getAuthor();
            case DATE_CHANGED:
                return fileAndCommit.getValue().getCommitterDate().toString();
            case REVISION:
                return fileAndCommit.getValue().getCommit().substring(0, 9);
            case MESSAGE:
                return fileAndCommit.getValue().getCommitMessage();
            default:
                return "";
        }
    }

    private Map<RevisionHistoryTableColumn, String> parseTableStructure(String tableStructureString) {
        if (StringUtils.isBlank(tableStructureString)) {
            return RevisionHistoryTableColumn.DEFAULT;
        }
        final Map<RevisionHistoryTableColumn, String> result = new LinkedHashMap<>();
        for (String column : tableStructureString.split(";")) {
            final String[] columnAndName = column.split("-");
            final RevisionHistoryTableColumn tableColumn = RevisionHistoryTableColumn.byValue(columnAndName[0]);
            if (columnAndName.length == 2) {
                result.put(tableColumn, columnAndName[1]);
            } else {
                result.put(tableColumn, tableColumn.value);
            }
        }
        return result;
    }

    enum RevisionHistoryTableColumn {

        REVISION("Revision"),
        DATE_CHANGED("Date changed"),
        PATH("Path"),
        AUTHOR("Author"),
        MESSAGE("Message");

        private static final Map<String, RevisionHistoryTableColumn> BY_VALUE = new HashMap<>();
        private static final Map<RevisionHistoryTableColumn, String> DEFAULT = new LinkedHashMap<>();

        static {
            BY_VALUE.put(PATH.value, PATH);
            BY_VALUE.put(REVISION.value, REVISION);
            BY_VALUE.put(DATE_CHANGED.value, DATE_CHANGED);
            BY_VALUE.put(AUTHOR.value, AUTHOR);
            BY_VALUE.put(MESSAGE.value, MESSAGE);

            for (RevisionHistoryTableColumn value : RevisionHistoryTableColumn.values()) {
                DEFAULT.put(value, value.value);
            }

        }

        private final String value;

        RevisionHistoryTableColumn(String value) {
            this.value = value;
        }

        public static RevisionHistoryTableColumn byValue(final String value) {
            return BY_VALUE.get(value);
        }
    }

}
