package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.amazonaws.util.CollectionUtils;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor.*;

import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

public enum VSReportTemplates {
    VERSIONED_STORAGE("versioned_storage", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) -> storage.getName())),
    PAGE_BREAK("page_break", () -> new VSReportTemplatePageBreakProcessor((paragraph, storage, diff) -> "")),
    REPORT_DATE("report_date", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) ->
            DateUtils.nowUTC().format(Constants.DATE_TIME_FORMATTER))),
    FILTER_AUTHORS("filter_authors", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) -> {
        if (CollectionUtils.isNullOrEmpty(diff.getFilters().getAuthors())) {
            return "All Authors";
        }
        return diff.getFilters().getAuthors();
    })),
    FILTER_FROM("filter_from", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) -> {
        if (diff.getFilters().getDateFrom() == null) {
            return "-";
        }
        return diff.getFilters().getDateFrom().format(Constants.DATE_TIME_FORMATTER);
    })),
    FILTER_TO("filter_to", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) -> {
        if (diff.getFilters().getDateTo() == null) {
            return "-";
        }
        return diff.getFilters().getDateTo().format(Constants.DATE_TIME_FORMATTER);
    })),
    FILTER_TYPES("filter_types", () -> new VSReportTemplateTextProcessor((paragraph, storage, diff) -> {
        if (CollectionUtils.isNullOrEmpty(diff.getFilters().getExtensions())) {
            return "All Files";
        }
        return diff.getFilters().getExtensions();
    })),
    FILE_LIST_TABLE("\"file_list_table\".*",
            () -> new VSReportTemplateTableProcessor(new FileListTableExtractor())),
    REVISION_HISTORY_TABLE("\"revision_history_table\".*",
            () -> new VSReportTemplateTableProcessor(new RevisionListTableExtractor())),
    COMMIT_DIFFS("commit_diffs", () -> new VSReportTemplateDiffProcessor(new CommitDiffExtractor()));

    public final String template;
    public final Supplier<VSReportTemplateProcessor> templateResolver;

    VSReportTemplates(final String template,
                      final Supplier<VSReportTemplateProcessor> templateResolver) {
        this.template = template;
        this.templateResolver = templateResolver;
    }

    private static class Constants {
        public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    }
}
