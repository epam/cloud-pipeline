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
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.VSReportTemplates;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGrouping;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGroupType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.DiffUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
@Slf4j
public class VersionStorageReportTemplateManager {

    public static final String HISTORY = "history";
    public static final String DOCX = ".docx";
    public static final String ZIP = ".zip";
    public static final SimpleDateFormat REPORT_FILE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
    public static final String NAME_SEPARATOR = "_";
    public static final String REVISION = "revision";
    public static final String SUMMARY = "summary";

    private final PipelineManager pipelineManager;
    private final GitManager gitManager;
    private final PreferenceManager preferenceManager;

    @Autowired
    public VersionStorageReportTemplateManager(final PipelineManager pipelineManager,
                                               final GitManager gitManager,
                                               final PreferenceManager preferenceManager) {
        this.pipelineManager = pipelineManager;
        this.gitManager = gitManager;
        this.preferenceManager = preferenceManager;
    }

    public Pair<String, byte[]> generateReport(final Long pipelineId,
                                               final GitDiffReportFilter reportFilters) {
        final Pipeline loaded = pipelineManager.load(pipelineId);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final GitDiff gitDiff = fetchAndNormalizeDiffs(loaded.getId(), reportFilters);
        try {
            final List<Pair<String, XWPFDocument>> diffReportFiles = prepareReportDocs(
                    loaded, gitDiff, reportFilters
            );

            if (diffReportFiles.isEmpty()) {
                throw new IllegalArgumentException("No data for report");
            }

            return Pair.of(writeReportToStream(loaded, outputStream, diffReportFiles), outputStream.toByteArray());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    private String writeReportToStream(final Pipeline loaded,
                                       final OutputStream outputStream,
                                       final List<Pair<String, XWPFDocument>> diffReportFiles) throws IOException {
        final String reportFileName;
        if (diffReportFiles.size() == 1) {
            reportFileName = HISTORY + NAME_SEPARATOR + loaded.getName() + NAME_SEPARATOR
                            + REPORT_FILE_NAME_DATE_FORMAT.format(DateUtils.now()) + DOCX;
            diffReportFiles.get(0).getSecond().write(outputStream);
        } else {
            reportFileName = HISTORY + NAME_SEPARATOR + loaded.getName() + NAME_SEPARATOR
                            + REPORT_FILE_NAME_DATE_FORMAT.format(DateUtils.now()) + ZIP;
            writeToZipStream(outputStream, diffReportFiles);
        }
        return reportFileName;
    }

    protected GitDiff fetchAndNormalizeDiffs(final Long pipelineId, final GitDiffReportFilter reportFilters) {
        final GitReaderDiff gitReaderDiff = gitManager.logRepositoryCommitDiffs(
                pipelineId, true, Optional.ofNullable(reportFilters.getCommitsFilter())
                        .orElse(GitCommitsFilter.builder().build())
        );
        return DiffUtils.reduceDiffByFile(gitReaderDiff);
    }

    private List<Pair<String, XWPFDocument>> prepareReportDocs(final Pipeline loaded,
                                                               final GitDiff gitDiff,
                                                               final GitDiffReportFilter reportFilters)
            throws IOException {
        final List<Pair<String, XWPFDocument>> results = new ArrayList<>();
        String versionStorageTemplatePath = getVersionStorageTemplatePath();
        Assert.notNull(versionStorageTemplatePath,
                "Version Storage Report Template not configured, please specify "
                        + SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE.getKey());
        final XWPFDocument report = new XWPFDocument(new FileInputStream(versionStorageTemplatePath));
        fillTemplate(report, loaded, gitDiff, reportFilters);
        results.add(Pair.of(SUMMARY + NAME_SEPARATOR + loaded.getName() + NAME_SEPARATOR +
                ReportDataExtractor.DATE_FORMAT.format(DateUtils.now()) + DOCX, report));
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
        return GitDiffGrouping.builder()
                .includeDiff(reportFilters.isIncludeDiff())
                .diffGrouping(
                        getGroupType(reportFilters) == GitDiffGroupType.BY_COMMIT
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
                                (getGroupType(reportFilters) == GitDiffGroupType.BY_COMMIT
                                        ? REVISION + NAME_SEPARATOR : ""
                                ) + p.getFirst().replace("/", NAME_SEPARATOR) + DOCX,
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

    private GitDiffGroupType getGroupType(final GitDiffReportFilter reportFilter) {
        if (reportFilter.getGroupType() == null) {
            return GitDiffGroupType.BY_COMMIT;
        }
        return reportFilter.getGroupType();
    }

    private String getVersionStorageTemplatePath() {
        return preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE);
    }

    public void fillTemplate(final XWPFDocument docxTemplate, final Pipeline storage,
                             final GitDiff diff, final GitDiffReportFilter reportFilter) {
        this.changeHeadersAndFooters(docxTemplate, storage, diff, reportFilter);
        this.changeBodyElements(docxTemplate.getBodyElements(), storage, diff, reportFilter);
    }

    private void changeHeadersAndFooters(final XWPFDocument document, final Pipeline storage,
                                         final GitDiff diff, final GitDiffReportFilter reportFilter) {
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

    private void changeHeader(final XWPFHeader header, final Pipeline storage, final GitDiff diff,
                              final GitDiffReportFilter reportFilter) {
        if (header == null) {
            return;
        }
        this.changeBodyElements(header.getBodyElements(), storage, diff, reportFilter);
    }

    private void changeFooter(final XWPFFooter footer, final Pipeline storage, final GitDiff diff,
                              final GitDiffReportFilter reportFilter) {
        if (footer == null) {
            return;
        }
        this.changeBodyElements(footer.getBodyElements(), storage, diff, reportFilter);
    }

    /**
     * Modifies elements. Replaces all occurrences of placeholders with corresponding values
     * @param getBodyElements is supplier which returns list of elements
     * @param storage - reported Version Storage
     * @param diff - Git diff object to be retrieved for the data
     * @param reportFilter - Filter object with date, commit information etc, to generate a report
     */
    private void changeBodyElements(final List<IBodyElement> getBodyElements, final Pipeline storage,
                                    final GitDiff diff, final GitDiffReportFilter reportFilter) {
        int size = getBodyElements.size();
        for (int i = 0; i < size; i++) {
            this.changeBodyElement(getBodyElements.get(i), storage, diff, reportFilter);
            size = getBodyElements.size();
        }
    }

    private void changeBodyElement(final IBodyElement element, final Pipeline storage, final GitDiff diff,
                                   final GitDiffReportFilter reportFilter) {
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
     * @param storage - reported Version Storage
     * @param diff - Git diff object to be retrieved for the data
     */
    private void changeParagraph(final XWPFParagraph paragraph, final Pipeline storage,
                                 final GitDiff diff, final GitDiffReportFilter reportFilter) {
        for (VSReportTemplates template : VSReportTemplates.values()) {
            template.templateResolver.get().process(paragraph, template.template, storage, diff, reportFilter);
        }
    }

    /**
     * Modifies word document's table. Replaces all occurrences of placeholders with corresponding values
     *
     * @param table XWPFTable to be modified
     * @param diff - Git diff object to be retrieved for the data
     */
    private void changeTable(final XWPFTable table, final Pipeline storage,
                             final GitDiff diff, final GitDiffReportFilter reportFilter) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                this.changeBodyElements(cell.getBodyElements(), storage, diff, reportFilter);
            }
        }
    }

    private void writeToZipStream(final OutputStream outputStream,
                                  final List<Pair<String, XWPFDocument>> diffReportFiles) throws IOException {
        final ZipOutputStream zipOut = new ZipOutputStream(outputStream);
        for (final Pair<String, XWPFDocument> diffReportFile : diffReportFiles) {
            final ByteArrayOutputStream dos = new ByteArrayOutputStream();
            diffReportFile.getSecond().write(dos);
            final InputStream bais = new ByteArrayInputStream(dos.toByteArray());
            zipOut.putNextEntry(new ZipEntry(diffReportFile.getFirst()));
            byte[] bytes = new byte[1024];
            int length;
            while ((length = bais.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
        zipOut.close();
    }
}
