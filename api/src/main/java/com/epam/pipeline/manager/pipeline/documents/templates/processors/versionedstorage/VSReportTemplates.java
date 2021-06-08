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

import com.amazonaws.util.CollectionUtils;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor.*;
import lombok.RequiredArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

@RequiredArgsConstructor
public enum VSReportTemplates {
    VERSIONED_STORAGE("versioned_storage", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff, reportFilter) -> storage.getName())),
    PAGE_BREAK("page_break", () -> new VSReportTemplatePageBreakProcessor((paragraph, storage, diff, reportFilter) -> "")),
    REPORT_DATE("report_date", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff, reportFilter) ->
            DateUtils.nowUTC().format(Constants.DATE_TIME_FORMATTER))),
    FILTER_AUTHORS("filter_authors", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff, reportFilter) -> {
        if (CollectionUtils.isNullOrEmpty(diff.getFilters().getAuthors())) {
            return "All Authors";
        }
        return diff.getFilters().getAuthors();
    })),
    FILTER_FROM("filter_from",
        () -> new VSReportTemplateTextProcessor(
            (paragraph, storage, diff, reportFilter) ->
                Optional.ofNullable(
                        diff.getFilters().getDateFrom()
                ).map(d -> d.format(Constants.DATE_TIME_FORMATTER)
                ).orElse("-"))
    ),
    FILTER_TO("filter_to",
        () -> new VSReportTemplateTextProcessor(
            (paragraph, storage, diff, reportFilter) ->
                Optional.ofNullable(
                        diff.getFilters().getDateTo()
                ).map(d -> d.format(Constants.DATE_TIME_FORMATTER)
                ).orElse("-"))
    ),
    FILTER_TYPES("filter_types",
        () -> new VSReportTemplateTextProcessor(
            (paragraph, storage, diff, reportFilter) -> {
                if (CollectionUtils.isNullOrEmpty(diff.getFilters().getExtensions())) {
                    return "All Files";
                }
                return diff.getFilters().getExtensions();
            }
        )
    ),
    FILE_LIST_TABLE("\"file_list_table\".*",
            () -> new VSReportTemplateTableProcessor(new FileListTableExtractor())),
    REVISION_HISTORY_TABLE("\"revision_history_table\".*",
            () -> new VSReportTemplateTableProcessor(new RevisionListTableExtractor())),
    COMMIT_DIFFS("commit_diffs", () -> new VSReportTemplateDiffProcessor(new CommitDiffExtractor()));

    public final String template;
    public final Supplier<VSReportTemplateProcessor> templateResolver;

    private static class Constants {
        public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    }
}
