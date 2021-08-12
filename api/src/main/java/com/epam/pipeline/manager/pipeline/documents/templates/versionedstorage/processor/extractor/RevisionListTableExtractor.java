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

import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.TableRow;
import com.epam.pipeline.manager.utils.DiffUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RevisionListTableExtractor implements ReportDataExtractor<Table> {

    private static final Pattern PATTERN = Pattern.compile("\\{\"revision_history_table\":?(.*)}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Table extract(final XWPFParagraph xwpfParagraph, final Pipeline storage,
                         final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        final Map<RevisionHistoryTableColumn, String> tableColumns = getTableColumns(xwpfParagraph);
        final Table result = new Table();
        result.setContainsHeaderRow(true);
        for (String value : tableColumns.values()) {
            result.addColumn(value);
        }

        diff.getEntries().stream()
                .sorted(Comparator.comparing(d -> d.getCommit().getAuthorDate()))
                .map(gitDiffEntry -> new Pair<>(
                        DiffUtils.getChangedFileName(gitDiffEntry.getDiff()),
                        gitDiffEntry.getCommit()))
                .forEachOrdered(fileAndCommit -> {
                    TableRow row = result.addRow(fileAndCommit.getKey() + fileAndCommit.getValue().getCommit());
                    tableColumns.forEach(
                        (e, v) -> result.setData(row.getName(), v,
                                e.dataExtractor.apply(fileAndCommit.getKey(), fileAndCommit.getValue()))
                    );
                });
        return result;
    }

    private Map<RevisionHistoryTableColumn, String> getTableColumns(final XWPFParagraph xwpfParagraph) {
        final Matcher matcher = PATTERN.matcher(xwpfParagraph.getText());
        final Map<RevisionHistoryTableColumn, String> tableColumns;
        if(matcher.matches()) {
            final String tableStructure = matcher.group(1);
            tableColumns = parseTableStructure(tableStructure);
        } else {
            tableColumns = Arrays.stream(RevisionHistoryTableColumn.values())
                    .collect(Collectors.toMap(c -> c, c -> c.defaultColumn));
        }
        return tableColumns;
    }

    private Map<RevisionHistoryTableColumn, String> parseTableStructure(final String tableStructureString) {
        if (StringUtils.isBlank(tableStructureString)) {
            return RevisionHistoryTableColumn.DEFAULT;
        }
        try {
            return OBJECT_MAPPER.readValue(
                    tableStructureString,
                    new TypeReference<LinkedHashMap<RevisionHistoryTableColumn, String>>() {}
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Report template is invalid. Possible columns: " +
                    Arrays.stream(RevisionHistoryTableColumn.values())
                            .map(c -> c.value).collect(Collectors.joining(", ")));
        }
    }

    @RequiredArgsConstructor
    public enum RevisionHistoryTableColumn {

        @JsonProperty("path")
        PATH("path", "Path", (file, commit) -> file),

        @JsonProperty("author")
        AUTHOR("author", "Author", (file, commit) -> commit.getAuthor()),

        @JsonProperty("date_changed")
        DATE_CHANGED("date_changed", "Date changed",
            (file, commit) -> DATE_FORMAT.format(commit.getAuthorDate())
        ),

        @JsonProperty("revision")
        REVISION("revision", "Revision", (file, commit) -> commit.getCommit().substring(0, 9)),

        @JsonProperty("message")
        MESSAGE("message", "Message", (file, commit) -> commit.getCommitMessage());

        private static final Map<RevisionHistoryTableColumn, String> DEFAULT = new LinkedHashMap<>();

        static {
            for (RevisionHistoryTableColumn value : RevisionHistoryTableColumn.values()) {
                DEFAULT.put(value, value.defaultColumn);
            }
        }

        private final String value;
        private final String defaultColumn;
        private final BiFunction<String, GitReaderRepositoryCommit, String> dataExtractor;

    }

}
