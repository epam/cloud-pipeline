/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.documents.templates;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffEntry;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.VSReportTemplates;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.CommitDiffsGrouping;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.DiffUtils;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
@Slf4j
public class VersionStorageReportTemplateManager {

    private final static String GIT_DIFF_HEADER = "diff --git";

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private PreferenceManager preferenceManager;

    public byte[] generateReport(final Long pipelineId,
                                 final GitDiffReportFilter reportFilters) {
        Pipeline loaded = pipelineManager.load(pipelineId);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GitDiff gitDiff = fetchAndNormalizeDiffs(loaded.getId(), reportFilters);
        try {
            final List<Pair<String, XWPFDocument>> diffReportFiles = prepareReportDocs(
                    loaded, gitDiff, reportFilters
            );

            if (diffReportFiles.isEmpty()) {
                return new byte[0];
            }

            if (diffReportFiles.size() == 1) {
                diffReportFiles.get(0).getSecond().write(outputStream);
            } else {
                ZipOutputStream zipOut = new ZipOutputStream(outputStream);
                for (Pair<String, XWPFDocument> diffReportFile : diffReportFiles) {
                    ByteArrayOutputStream dos = new ByteArrayOutputStream();
                    diffReportFile.getSecond().write(dos);
                    InputStream fis = new ByteArrayInputStream(dos.toByteArray());
                    ZipEntry zipEntry = new ZipEntry(diffReportFile.getFirst());
                    zipOut.putNextEntry(zipEntry);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                }
                zipOut.close();
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    protected GitDiff fetchAndNormalizeDiffs(final Long pipelineId, final GitDiffReportFilter reportFilters) {
        final DiffParser diffParser = new UnifiedDiffParser();
        final GitReaderDiff gitReaderDiff = gitManager.logRepositoryCommitDiffs(
                pipelineId, true, Optional.ofNullable(reportFilters.getCommitsFilter())
                        .orElse(GitCommitsFilter.builder().build())
        );
        return GitDiff.builder()
                .entries(
                        gitReaderDiff.getEntries().stream().flatMap(diff -> {
                            String[] diffsByFile = diff.getDiff().split(GIT_DIFF_HEADER);
                            return Arrays.stream(diffsByFile)
                                    .filter(StringUtils::isNotBlank)
                                    .map(fileDiff -> {
                                        try {
                                            final Diff parsed = diffParser.parse(
                                                    (GIT_DIFF_HEADER + fileDiff).getBytes(StandardCharsets.UTF_8)
                                            ).stream().findFirst().orElseThrow(IllegalArgumentException::new);
                                            return GitDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.normalizeDiff(parsed)).build();
                                        } catch (Exception e) {
                                            // If we fail to parse diff with diffParser lets
                                            // try to parse it as binary diffs
                                            return GitDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.parseBinaryDiff(GIT_DIFF_HEADER + fileDiff))
                                                    .build();
                                        }
                                    });
                            }).collect(Collectors.toList())
                ).filters(gitReaderDiff.getFilters()).build();
    }

    private List<Pair<String, XWPFDocument>> prepareReportDocs(final Pipeline loaded, final GitDiff gitDiff,
                                                 final GitDiffReportFilter reportFilters) throws IOException {
        final List<Pair<String, XWPFDocument>> results = new ArrayList<>();
        final XWPFDocument report = new XWPFDocument(
                new FileInputStream(getVersionStorageTemplatePath())
        );
        fillTemplate(report, loaded, gitDiff, reportFilters);
        results.add(Pair.of("vs_report_" + loaded.getName() + "_" + ReportDataExtractor.DATE_FORMAT.format(DateUtils.now()) + ".docx", report));
        if (reportFilters.isArchive()) {
            results.addAll(
                    prepareDiffsForReportDoc(loaded, gitDiff,
                        GitDiffReportFilter.builder()
                            .groupType(reportFilters.getGroupType())
                            .commitsFilter(reportFilters.getCommitsFilter())
                            .includeDiff(reportFilters.isIncludeDiff())
                            .build()
                    )
            );
        }
        return results;
    }

    private List<Pair<String, XWPFDocument>> prepareDiffsForReportDoc(final Pipeline loaded, final GitDiff gitDiff,
                                                             final GitDiffReportFilter reportFilters) {
        return CommitDiffsGrouping.builder()
                .includeDiff(reportFilters.isIncludeDiff())
                .diffGrouping(
                        getGroupType(reportFilters) == CommitDiffsGrouping.GroupType.BY_COMMIT
                            ? gitDiff.getEntries().stream()
                            .collect(Collectors.groupingBy(e -> e.getCommit().getCommit()))
                            : gitDiff.getEntries().stream()
                            .collect(Collectors.groupingBy(e -> e.getDiff().getToFileName()))
                ).build().getDiffGrouping()
                .entrySet()
                .stream()
                .map(e -> Pair.of(e.getKey(), e.getValue()))
                .map(pair ->
                        Pair.of(
                            pair.getFirst(),
                            GitDiff.builder()
                                .entries(pair.getSecond())
                                .filters(gitDiff.getFilters())
                                .build()
                        )
                ).map(p -> {
                    try {
                        final XWPFDocument report = new XWPFDocument(
                                new FileInputStream(getVersionStorageTemplatePath())
                        );
                        int toDelete = 0;
                        while (report.getBodyElements().size() != 1) {
                            IBodyElement element = report.getBodyElements().get(toDelete);
                            if (element.getElementType() == BodyElementType.PARAGRAPH &&
                                    ((XWPFParagraph) element).getText()
                                            .contains(VSReportTemplates.COMMIT_DIFFS.template)) {
                                toDelete += 1;
                            }
                            report.removeBodyElement(toDelete);
                        }
                        fillTemplate(report, loaded, p.getSecond(), reportFilters);
                        return Pair.of(
                                (getGroupType(reportFilters) == CommitDiffsGrouping.GroupType.BY_COMMIT
                                        ? "revision_" : "file_"
                                ) + p.getFirst().replace("/", "_") + ".docx",
                                report
                        );
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private CommitDiffsGrouping.GroupType getGroupType(GitDiffReportFilter reportFilter) {
        if (reportFilter.getGroupType() == null) {
            return CommitDiffsGrouping.GroupType.BY_COMMIT;
        }
        return reportFilter.getGroupType();
    }

    private String getVersionStorageTemplatePath() {
        return preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE);
    }

    public void fillTemplate(XWPFDocument docxTemplate, Pipeline storage,
                             GitDiff diff, GitDiffReportFilter reportFilter) {
        this.changeHeadersAndFooters(docxTemplate, storage, diff, reportFilter);
        this.changeBodyElements(docxTemplate::getBodyElements, storage, diff, reportFilter);
    }

    private void changeHeadersAndFooters(XWPFDocument document, Pipeline storage, GitDiff diff,
                                         GitDiffReportFilter reportFilter) {
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        this.changeHeader(policy.getDefaultHeader(), storage, diff, reportFilter);
        this.changeFooter(policy.getDefaultFooter(), storage, diff, reportFilter);
        this.changeHeader(policy.getEvenPageHeader(), storage, diff, reportFilter);
        this.changeFooter(policy.getEvenPageFooter(), storage, diff, reportFilter);
        this.changeHeader(policy.getOddPageHeader(), storage, diff, reportFilter);
        this.changeFooter(policy.getEvenPageFooter(), storage, diff, reportFilter);
        for (XWPFHeader header : document.getHeaderList()) {
            this.changeHeader(header, storage, diff, reportFilter);
        }
    }

    private void changeHeader(XWPFHeader header, Pipeline storage, GitDiff diff, GitDiffReportFilter reportFilter) {
        if (header == null) {
            return;
        }
        this.changeBodyElements(header::getBodyElements, storage, diff, reportFilter);
    }

    private void changeFooter(XWPFFooter footer, Pipeline storage, GitDiff diff, GitDiffReportFilter reportFilter) {
        if (footer == null) {
            return;
        }
        this.changeBodyElements(footer::getBodyElements, storage, diff, reportFilter);
    }

    /**
     * Modifies elements. Replaces all occurrences of placeholders with corresponding values
     * @param getBodyElements is supplier which returns list of elements
     * @param storage
     * @param diff
     * @param reportFilter
     */
    private void changeBodyElements(Supplier<List<IBodyElement>> getBodyElements, Pipeline storage, GitDiff diff,
                                    GitDiffReportFilter reportFilter) {
        int size = getBodyElements.get().size();
        for (int i = 0; i < size; i++) {
            this.changeBodyElement(getBodyElements.get().get(i), storage, diff, reportFilter);
            size = getBodyElements.get().size();
        }
    }

    private void changeBodyElement(IBodyElement element, Pipeline storage, GitDiff diff,
                                   GitDiffReportFilter reportFilter) {
        switch (element.getElementType()) {
            case TABLE:
                this.changeTable((XWPFTable) element, storage, diff, reportFilter);
                break;
            case PARAGRAPH:
                this.changeParagraph((XWPFParagraph) element, storage, diff, reportFilter);
                break;
            default:
                break;
        }
    }

    /**
     * Modifies word document's paragraph. Replaces all occurrences of placeholders with corresponding values
     *  @param paragraph paragraph to be modified
     * @param storage
     * @param diff
     */
    private void changeParagraph(XWPFParagraph paragraph, Pipeline storage, GitDiff diff,
                                 GitDiffReportFilter reportFilter) {
        for (VSReportTemplates template : VSReportTemplates.values()) {
            template.templateResolver.get().process(paragraph, template.template, storage, diff, reportFilter);
        }
    }

    /**
     * Modifies word document's table. Replaces all occurrences of placeholders with corresponding values
     *
     * @param table XWPFTable to be modified
     * @param diff
     */
    private void changeTable(XWPFTable table, Pipeline storage, GitDiff diff, GitDiffReportFilter reportFilter) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                this.changeBodyElements(cell::getBodyElements, storage, diff, reportFilter);
            }
        }
    }
}
