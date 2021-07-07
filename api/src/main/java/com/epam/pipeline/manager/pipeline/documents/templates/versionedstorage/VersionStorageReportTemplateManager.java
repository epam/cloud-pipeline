/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.report.VersionStorageReportFile;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor.extractor.ReportDataExtractor;
import com.epam.pipeline.entity.git.report.GitDiffGroupType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.DiffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
@Slf4j
@RequiredArgsConstructor
public class VersionStorageReportTemplateManager {

    public static final String HISTORY = "history";
    public static final String DOCX = ".docx";
    public static final String ZIP = ".zip";
    public static final SimpleDateFormat REPORT_FILE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
    public static final String NAME_SEPARATOR = "_";
    public static final String REVISION = "revision";
    public static final String SUMMARY = "summary";
    public static final int BUFFER_SIZE = 1024;
    public static final String EMPTY = "";
    public static final String COMMA = ",";

    private final PipelineManager pipelineManager;
    private final GitManager gitManager;
    private final PreferenceManager preferenceManager;

    public VersionStorageReportFile generateReport(final Long pipelineId,
                                                   final GitDiffReportFilter reportFilters) {
        final Pipeline pipeline = pipelineManager.load(pipelineId);
        final GitParsedDiff gitDiff = fetchAndNormalizeDiffs(pipeline.getId(), reportFilters);
        try {
            final List<Pair<String, XWPFDocument>> diffReportFiles = prepareReportDocs(
                    pipeline, gitDiff, reportFilters
            );

            if (diffReportFiles.isEmpty()) {
                throw new IllegalArgumentException("No data for report");
            }

            return VersionStorageReportFile.builder()
                    .name(resolveReportName(pipeline, diffReportFiles))
                    .content(writeReport(diffReportFiles))
                    .build();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    private byte[] writeReport(final List<Pair<String, XWPFDocument>> diffReportFiles) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (diffReportFiles.size() == 1) {
            diffReportFiles.get(0).getSecond().write(outputStream);
        } else {
            writeToZipStream(outputStream, diffReportFiles);
        }
        return outputStream.toByteArray();
    }

    private String resolveReportName(final Pipeline loaded,
                                     final List<Pair<String, XWPFDocument>> diffReportFiles) {
        return HISTORY + NAME_SEPARATOR + loaded.getName()
                + NAME_SEPARATOR
                + REPORT_FILE_NAME_DATE_FORMAT.format(DateUtils.now())
                + resolveFileExtension(diffReportFiles);
    }

    private String resolveFileExtension(final List<Pair<String, XWPFDocument>> diffReportFiles) {
        return diffReportFiles.size() > 1 ? ZIP : DOCX;
    }

    protected GitParsedDiff fetchAndNormalizeDiffs(final Long pipelineId, final GitDiffReportFilter reportFilters) {
        final GitReaderDiff gitReaderDiff = gitManager.logRepositoryCommitDiffs(
                pipelineId, true, Optional.ofNullable(reportFilters.getCommitsFilter())
                        .orElse(GitCommitsFilter.builder().build())
        );
        return DiffUtils.reduceDiffByFile(gitReaderDiff, reportFilters);
    }

    private List<Pair<String, XWPFDocument>> prepareReportDocs(final Pipeline pipeline,
                                                               final GitParsedDiff gitDiff,
                                                               final GitDiffReportFilter reportFilters)
            throws IOException {
        final List<Pair<String, XWPFDocument>> results = new ArrayList<>();
        final XWPFDocument report = new XWPFDocument(new FileInputStream(getVersionStorageTemplatePath()));
        fillTemplate(report, pipeline, gitDiff, reportFilters);
        results.add(Pair.of(SUMMARY + NAME_SEPARATOR + pipeline.getName() + NAME_SEPARATOR +
                ReportDataExtractor.DATE_FORMAT.format(DateUtils.now()) + DOCX, report));
        if (reportFilters.isArchive()) {
            results.addAll(prepareDiffsForReportDoc(
                    pipeline, gitDiff, reportFilters.toBuilder().archive(false).build()));
        }
        return results;
    }

    private List<Pair<String, XWPFDocument>> prepareDiffsForReportDoc(final Pipeline loaded,
                                                                      final GitParsedDiff gitDiff,
                                                                      final GitDiffReportFilter reportFilters) {
        final Map<String, List<GitParsedDiffEntry>> diffGrouping =
                getGroupType(reportFilters) == GitDiffGroupType.BY_COMMIT
                        ? gitDiff.getEntries().stream().collect(
                                Collectors.groupingBy(e -> e.getCommit().getCommit()))
                        : gitDiff.getEntries().stream().collect(
                                Collectors.groupingBy(e -> DiffUtils.getChangedFileName(e.getDiff())));

        return diffGrouping.entrySet()
                .stream()
                .map(entry -> {
                    final String reportId = entry.getKey();
                    final GitParsedDiff parsedDiff = GitParsedDiff.builder()
                            .entries(entry.getValue()).filters(gitDiff.getFilters()).build();
                    try {
                        final XWPFDocument report = prepareGroupReportTemplate(getVersionStorageTemplatePath());
                        fillTemplate(report, loaded, parsedDiff, reportFilters);
                        return Pair.of(resolveGroupReportFileName(reportFilters, reportId), report);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void fillTemplate(final XWPFDocument docxTemplate, final Pipeline storage,
                             final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        changeHeadersAndFooters(docxTemplate, storage, diff, reportFilter);
        changeBodyElements(docxTemplate.getBodyElements(), storage, diff, reportFilter);
    }

    private void changeHeadersAndFooters(final XWPFDocument document, final Pipeline storage,
                                         final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        changeHeaderFooter(policy.getDefaultHeader(), storage, diff, reportFilter);
        changeHeaderFooter(policy.getDefaultFooter(), storage, diff, reportFilter);
        changeHeaderFooter(policy.getEvenPageHeader(), storage, diff, reportFilter);
        changeHeaderFooter(policy.getEvenPageFooter(), storage, diff, reportFilter);
        changeHeaderFooter(policy.getOddPageHeader(), storage, diff, reportFilter);
        changeHeaderFooter(policy.getOddPageFooter(), storage, diff, reportFilter);
        for (XWPFHeader header : document.getHeaderList()) {
            changeHeaderFooter(header, storage, diff, reportFilter);
        }
    }

    private void changeHeaderFooter(final XWPFHeaderFooter headerFooter, final Pipeline storage,
                                    final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        if (headerFooter == null) {
            return;
        }
        changeBodyElements(headerFooter.getBodyElements(), storage, diff, reportFilter);
    }

    /**
     * Modifies elements. Replaces all occurrences of placeholders with corresponding values
     * @param getBodyElements is supplier which returns list of elements
     * @param storage - reported Version Storage
     * @param diff - Git diff object to be retrieved for the data
     * @param reportFilter - Filter object with date, commit information etc, to generate a report
     */
    private void changeBodyElements(final List<IBodyElement> getBodyElements, final Pipeline storage,
                                    final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        int currentElement = 0;
        // we need to check size every time because after replacing some placeholder
        // with a value there could be more elements then before(inserting of several paragraphs),
        // because apache poi gives you modifiable list of body elements,
        // and if documents is changed that list also could be changed
        while (currentElement < getBodyElements.size()) {
            changeBodyElement(getBodyElements.get(currentElement), storage, diff, reportFilter);
            currentElement += 1;
        }
    }

    private void changeBodyElement(final IBodyElement element, final Pipeline storage,
                                   final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        switch (element.getElementType()) {
            case TABLE:
                changeTable((XWPFTable) element, storage, diff, reportFilter);
                break;
            case PARAGRAPH:
                changeParagraph((XWPFParagraph) element, storage, diff, reportFilter);
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
                                 final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        for (VSReportTemplates template : VSReportTemplates.values()) {
            template.templateResolver.get().process(paragraph, template.template, storage, diff, reportFilter,
                    getCustomBinaryExtension());
        }
    }

    /**
     * Modifies word document's table. Replaces all occurrences of placeholders with corresponding values
     *
     * @param table XWPFTable to be modified
     * @param diff - Git diff object to be retrieved for the data
     */
    private void changeTable(final XWPFTable table, final Pipeline storage,
                             final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        Optional.ofNullable(table).map(Stream::of).orElseGet(Stream::empty)
                .map(XWPFTable::getRows).flatMap(List::stream)
                .map(XWPFTableRow::getTableCells).flatMap(List::stream)
                .map(XWPFTableCell::getBodyElements)
                .forEach(elements -> changeBodyElements(elements, storage, diff, reportFilter));
    }

    private GitDiffGroupType getGroupType(final GitDiffReportFilter reportFilter) {
        return Optional.ofNullable(reportFilter.getGroupType()).orElse(GitDiffGroupType.BY_COMMIT);
    }

    private String getVersionStorageTemplatePath() {
        final String versionStorageTemplatePath = preferenceManager.getPreference(
                SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE);
        Assert.isTrue(!StringUtils.isEmpty(versionStorageTemplatePath),
                "Version Storage Report Template not configured, please specify "
                        + SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE.getKey());
        Assert.isTrue(Paths.get(versionStorageTemplatePath).toFile().isFile(),
                "Version Storage Report Template path doesn't exists " + versionStorageTemplatePath);
        return versionStorageTemplatePath;
    }

    private List<String> getCustomBinaryExtension() {
        return Arrays.stream(Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_BINARY_FILE_EXTS))
                .orElse(EMPTY)
                .split(COMMA))
                .map(String::trim).collect(Collectors.toList());
    }

    private String resolveGroupReportFileName(final GitDiffReportFilter reportFilters, final String id) {
        return (getGroupType(reportFilters) == GitDiffGroupType.BY_COMMIT ? REVISION + NAME_SEPARATOR : EMPTY)
                + id.replace("/", NAME_SEPARATOR) + DOCX;
    }

    private XWPFDocument prepareGroupReportTemplate(final String reportTemplatePath) throws IOException {
        // Here we clean up report template in order to leave only one template for commit diff
        // because here we generate report file only with diff information without any common info
        final XWPFDocument report = new XWPFDocument(new FileInputStream(reportTemplatePath));
        int currentElementIndex = 0;
        while (currentElementIndex < report.getBodyElements().size() - 1) {
            final IBodyElement element = report.getBodyElements().get(currentElementIndex);
            if (paragraphContainsTemplate(element, VSReportTemplates.COMMIT_DIFFS.template)) {
                currentElementIndex++;
                continue;
            }
            report.removeBodyElement(currentElementIndex);
        }
        return report;
    }

    private boolean paragraphContainsTemplate(final IBodyElement element, final String template) {
        return element.getElementType() == BodyElementType.PARAGRAPH &&
                ((XWPFParagraph) element).getText().contains(template);
    }

    private void writeToZipStream(final OutputStream outputStream,
                                  final List<Pair<String, XWPFDocument>> diffReportFiles) throws IOException {
        final ZipOutputStream zipOut = new ZipOutputStream(outputStream);
        for (final Pair<String, XWPFDocument> diffReportFile : diffReportFiles) {
            final ByteArrayOutputStream dos = new ByteArrayOutputStream();
            diffReportFile.getSecond().write(dos);
            final InputStream bais = new ByteArrayInputStream(dos.toByteArray());
            zipOut.putNextEntry(new ZipEntry(diffReportFile.getFirst()));
            byte[] bytes = new byte[BUFFER_SIZE];
            int length;
            while ((length = bais.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
        zipOut.close();
    }
}
