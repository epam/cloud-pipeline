package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.TableRow;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileListTableExtractor implements ReportDataExtractor {

    private final static Pattern PATTERN = Pattern.compile("\\{\"file_list_table\":?(.*)}");
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage, final GitDiff diff, GitDiffReportFilter reportFilter) {
        Matcher matcher = PATTERN.matcher(xwpfParagraph.getText());
        final Map<FileListTableColumn, String> tableColumns;
        if(matcher.matches()) {
            final String tableStructure = matcher.group(1);
            tableColumns = parseTableStructure(tableStructure);
        } else {
            tableColumns = Arrays.stream(FileListTableColumn.values())
                    .collect(Collectors.toMap(c -> c, c -> c.defaultColumn));
        }
        final Table result = new Table();
        result.setContainsHeaderRow(true);
        for (String value : tableColumns.values()) {
            result.addColumn(value);
        }
        final Set<String> reported = new HashSet<>();
        diff.getEntries().stream()
            .map(gitDiffEntry -> {
                final String file = gitDiffEntry.getDiff().getToFileName().equals("/dev/null")
                        ? gitDiffEntry.getDiff().getFromFileName()
                        : gitDiffEntry.getDiff().getToFileName();
                return new Pair<>(file, gitDiffEntry.getCommit());
            }).forEachOrdered(fileAndCommit -> {
                if (!reported.contains(fileAndCommit.getKey())) {
                    TableRow row = result.addRow(fileAndCommit.getKey());
                    tableColumns.forEach((e, v) -> result.setData(row.getName(), v, getData(fileAndCommit, e)));
                    reported.add(fileAndCommit.getKey());
                }
        });
        return result;
    }

    private String getData(Pair<String, GitReaderRepositoryCommit> fileAndCommit, FileListTableColumn e) {
        switch (e) {
            case PATH:
                return fileAndCommit.getKey();
            case NAME:
                return FilenameUtils.getName(fileAndCommit.getKey());
            case AUTHOR:
                return fileAndCommit.getValue().getAuthor();
            case DATE_CHANGED:
                return DATE_FORMAT.format(fileAndCommit.getValue().getAuthorDate());
            case REVISION:
                return fileAndCommit.getValue().getCommit().substring(0, 9);
            case MESSAGE:
                return fileAndCommit.getValue().getCommitMessage();
            default:
                return "";
        }
    }

    private Map<FileListTableColumn, String> parseTableStructure(String tableStructureString) {
        if (StringUtils.isBlank(tableStructureString)) {
            return FileListTableColumn.DEFAULT;
        }
        try {
            return OBJECT_MAPPER.readValue(
                    tableStructureString,
                    new TypeReference<LinkedHashMap<FileListTableColumn, String>>() {}
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Report template is invalid. Possible columns: " +
                    Arrays.stream(FileListTableColumn.values())
                            .map(c -> c.value).collect(Collectors.joining(", ")));
        }
    }

    public enum FileListTableColumn {

        @JsonProperty("name")
        NAME("name", "Name"),

        @JsonProperty("path")
        PATH("path", "Path"),

        @JsonProperty("revision")
        REVISION("revision", "Revision"),

        @JsonProperty("date_changed")
        DATE_CHANGED("date_changed", "Date changed"),

        @JsonProperty("author")
        AUTHOR("author", "Author"),

        @JsonProperty("message")
        MESSAGE("message", "Message");

        private static final Map<String, FileListTableColumn> BY_VALUE = new HashMap<>();
        private static final Map<FileListTableColumn, String> DEFAULT = new LinkedHashMap<>();

        static {
            BY_VALUE.put(NAME.value, NAME);
            BY_VALUE.put(PATH.value, PATH);
            BY_VALUE.put(REVISION.value, REVISION);
            BY_VALUE.put(DATE_CHANGED.value, DATE_CHANGED);
            BY_VALUE.put(AUTHOR.value, AUTHOR);
            BY_VALUE.put(MESSAGE.value, MESSAGE);

            for (FileListTableColumn value : FileListTableColumn.values()) {
                DEFAULT.put(value, value.defaultColumn);
            }
        }

        private final String value;
        private final String defaultColumn;

        FileListTableColumn(String value, String defaultColumn) {
            this.value = value;
            this.defaultColumn = defaultColumn;
        }

        public static FileListTableColumn byValue(final String value) {
            return BY_VALUE.get(value);
        }
    }

}
