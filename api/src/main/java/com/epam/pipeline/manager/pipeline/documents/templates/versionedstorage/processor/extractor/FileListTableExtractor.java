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
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.TableRow;
import com.epam.pipeline.manager.utils.DiffUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
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

public class FileListTableExtractor implements ReportDataExtractor<Table> {

    private static final Pattern PATTERN = Pattern.compile("\\{\"file_list_table\":?(.*)}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Table extract(final XWPFParagraph xwpfParagraph, final Pipeline storage,
                         final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        final Map<FileListTableColumn, String> tableColumns = getTableColumns(xwpfParagraph);
        final Table result = new Table();
        result.setContainsHeaderRow(true);
        for (String value : tableColumns.values()) {
            result.addColumn(value);
        }
        diff.getEntries()
            .stream()
            .sorted(Comparator.comparing(d -> DiffUtils.getChangedFileName(d.getDiff())))
            .collect(
                Collectors.toMap(
                    e -> DiffUtils.getChangedFileName(e.getDiff()),
                    GitParsedDiffEntry::getCommit,
                    (c1, c2) -> c1,
                    LinkedHashMap::new
                )
            ).forEach((file, commit) -> {
                TableRow row = result.addRow(file);
                tableColumns.forEach(
                    (columnType, column) ->
                            result.setData(row.getName(), column, columnType.dataExtractor.apply(file, commit)));
            });
        return result;
    }

    private Map<FileListTableColumn, String> getTableColumns(final XWPFParagraph xwpfParagraph) {
        final Matcher matcher = PATTERN.matcher(xwpfParagraph.getText());
        final Map<FileListTableColumn, String> tableColumns;
        if(matcher.matches()) {
            final String tableStructure = matcher.group(1);
            tableColumns = parseTableStructure(tableStructure);
        } else {
            tableColumns = Arrays.stream(FileListTableColumn.values())
                    .collect(Collectors.toMap(c -> c, c -> c.defaultColumn));
        }
        return tableColumns;
    }

    private Map<FileListTableColumn, String> parseTableStructure(final String tableStructureString) {
        if (StringUtils.isBlank(tableStructureString)) {
            return FileListTableColumn.DEFAULT;
        }
        try {
            return OBJECT_MAPPER.readValue(
                    tableStructureString
                            // replacing word's quotas with the real one
                            .replace("”", "\"")
                            .replace("“", "\""),
                    new TypeReference<LinkedHashMap<FileListTableColumn, String>>() {}
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Report template is invalid. Possible columns: " +
                    Arrays.stream(FileListTableColumn.values())
                            .map(c -> c.value).collect(Collectors.joining(", ")));
        }
    }

    @RequiredArgsConstructor
    public enum FileListTableColumn {

        @JsonProperty("name")
        NAME("name", "Name", (file, commit) -> FilenameUtils.getName(file)),

        @JsonProperty("path")
        PATH("path", "Path", (file, commit) -> file),

        @JsonProperty("revision")
        REVISION("revision", "Revision", (file, commit) -> commit.getCommit().substring(0, 9)),

        @JsonProperty("date_changed")
        DATE_CHANGED("date_changed", "Date changed",
            (file, commit) -> DATE_FORMAT.format(commit.getAuthorDate())
        ),

        @JsonProperty("author")
        AUTHOR("author", "Author", (file, commit) -> commit.getAuthor()),

        @JsonProperty("message")
        MESSAGE("message", "Message", (file, commit) -> commit.getCommitMessage());

        private static final Map<FileListTableColumn, String> DEFAULT = new LinkedHashMap<>();

        static {
            for (FileListTableColumn value : FileListTableColumn.values()) {
                DEFAULT.put(value, value.defaultColumn);
            }
        }

        private final String value;
        private final String defaultColumn;
        private final BiFunction<String, GitReaderRepositoryCommit, String> dataExtractor;

    }

}
