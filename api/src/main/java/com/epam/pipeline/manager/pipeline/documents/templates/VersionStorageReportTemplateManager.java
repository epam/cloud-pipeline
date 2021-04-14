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
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.VSReportTemplates;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.DiffUtils;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


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

    public byte[] generateReport(final Long pipelineId, final Boolean includeDiffs,
                                 final GitCommitsFilter reportFilters,
                                 final String templatePath) {
        try {
            Pipeline loaded = pipelineManager.load(pipelineId);
            FileInputStream inputStream = new FileInputStream(getVersionStorageTemplatePath(templatePath));
            XWPFDocument document = new XWPFDocument(inputStream);
            fillTemplate(document, loaded, fetchAndNormalizeDiffs(pipelineId, reportFilters), includeDiffs);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    protected GitDiff fetchAndNormalizeDiffs(final Long pipelineId, final GitCommitsFilter reportFilters) {
        final DiffParser diffParser = new UnifiedDiffParser();
        final GitReaderDiff gitReaderDiff = gitManager.logRepositoryCommitDiffs(
                pipelineId, true, reportFilters
        );
        return GitDiff.builder()
                .entries(
                        gitReaderDiff.getEntries().stream().flatMap(diff -> {
                            String[] diffsByFile = diff.getDiff().split(GIT_DIFF_HEADER);
                            return Arrays.stream(diffsByFile)
                                    .filter(StringUtils::isNotBlank)
                                    .map(fileDiff ->
                                    {
                                        try {
                                            final Diff parsed = diffParser.parse(
                                                    (GIT_DIFF_HEADER + fileDiff).getBytes(StandardCharsets.UTF_8)
                                            ).stream().findFirst().orElseThrow(IllegalArgumentException::new);
                                            return GitDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.normalizeDiff(parsed)).build();
                                        } catch (Exception e) {
                                            // If we fail to parse diff with diffParser lets try to parse it as binary diffs
                                            return GitDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.parseBinaryDiff(GIT_DIFF_HEADER + fileDiff))
                                                    .build();
                                        }
                                    });
                            }).collect(Collectors.toList())
                )
                .filters(gitReaderDiff.getFilters()).build();
    }


    private String getVersionStorageTemplatePath(String templatePath) {
        if (StringUtils.isNotBlank(templatePath)) {
            try {
                if (Files.exists(Paths.get(templatePath))) {
                    return templatePath;
                }
            } catch (Exception e) {
                return preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE);
            }
        }
        return preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE);
    }

    public void fillTemplate(XWPFDocument docxTemplate, Pipeline storage, GitDiff diff, Boolean includeDiffs) {
        this.changeHeadersAndFooters(docxTemplate, storage, diff);
        this.changeBodyElements(docxTemplate::getBodyElements, storage, diff);
    }

    private void changeHeadersAndFooters(XWPFDocument document, Pipeline storage, GitDiff diff) {
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        this.changeHeader(policy.getDefaultHeader(), storage, diff);
        this.changeFooter(policy.getDefaultFooter(), storage, diff);
        this.changeHeader(policy.getEvenPageHeader(), storage, diff);
        this.changeFooter(policy.getEvenPageFooter(), storage, diff);
        this.changeHeader(policy.getOddPageHeader(), storage, diff);
        this.changeFooter(policy.getEvenPageFooter(), storage, diff);
        for (XWPFHeader header : document.getHeaderList()) {
            this.changeHeader(header, storage, diff);
        }
    }

    private void changeHeader(XWPFHeader header, Pipeline storage, GitDiff diff) {
        if (header == null) {
            return;
        }
        this.changeBodyElements(header::getBodyElements, storage, diff);
    }

    private void changeFooter(XWPFFooter footer, Pipeline storage, GitDiff diff) {
        if (footer == null) {
            return;
        }
        this.changeBodyElements(footer::getBodyElements, storage, diff);
    }

    /**
     * Modifies elements. Replaces all occurrences of placeholders with corresponding values
     *  @param getBodyElements is supplier which returns list of elements
     * @param storage
     * @param diff
     */
    private void changeBodyElements(Supplier<List<IBodyElement>> getBodyElements, Pipeline storage, GitDiff diff) {
        int size = getBodyElements.get().size();
        for (int i = 0; i < size; i++) {
            this.changeBodyElement(getBodyElements.get().get(i), storage, diff);
            size = getBodyElements.get().size();
        }
    }

    private void changeBodyElement(IBodyElement element, Pipeline storage, GitDiff diff) {
        switch (element.getElementType()) {
            case TABLE:
                this.changeTable((XWPFTable) element, storage, diff);
                break;
            case PARAGRAPH:
                this.changeParagraph((XWPFParagraph) element, storage, diff);
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
    private void changeParagraph(XWPFParagraph paragraph, Pipeline storage, GitDiff diff) {
        for (VSReportTemplates template : VSReportTemplates.values()) {
            template.templateResolver.get().process(paragraph, template.template, storage, diff);
        }
    }

    /**
     * Modifies word document's table. Replaces all occurrences of placeholders with corresponding values
     *
     * @param table XWPFTable to be modified
     * @param diff
     */
    private void changeTable(XWPFTable table, Pipeline storage, GitDiff diff) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                this.changeBodyElements(cell::getBodyElements, storage, diff);
            }
        }
    }
}
