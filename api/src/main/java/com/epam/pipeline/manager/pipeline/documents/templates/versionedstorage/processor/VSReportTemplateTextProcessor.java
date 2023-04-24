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

package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor;

import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor.extractor.ReportDataExtractor;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class VSReportTemplateTextProcessor implements VSReportTemplateProcessor {

    private static final String EMPTY = "";

    private final ReportDataExtractor<String> dataProducer;

    public void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                                           final GitParsedDiff diff, final GitDiffReportFilter reportFilter,
                                           final List<String> customBinaryExtension) {

        final String replaceRegex = "(?i)\\{" + template + "}";
        final Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(paragraph.getText().replace("\t", ""));
        if (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            int globalStartIndex = 0;
            boolean dataInserted = false;
            for (XWPFRun run : paragraph.getRuns()) {
                for (int pos = 0; pos < run.getCTR().sizeOfTArray(); pos++) {

                    String runText = run.getText(pos);
                    if (runText == null) {
                        continue;
                    }
                    int globalEndIndex = globalStartIndex + runText.length();
                    if (globalStartIndex > end || globalEndIndex < start) {
                        globalStartIndex = globalEndIndex;
                        continue;
                    }
                    int replaceFrom = Math.max(globalStartIndex, start) - globalStartIndex;
                    int replaceTo = Math.min(globalEndIndex, end) - globalStartIndex;
                    // Since it is possible that placeholder text can be split on several runs inside a paragraph
                    // we need to replace part of placeholder with data only once, so lets replace it as soon
                    // as we on appropriate position and save state of in in dataInserted
                    if (replaceTo - replaceFrom > 0 && !dataInserted) {
                        runText = runText.substring(0, replaceFrom)
                                .concat(
                                        Optional.ofNullable(
                                                dataProducer.extract(paragraph, storage, diff, reportFilter))
                                                .orElse(EMPTY)
                                ).concat(runText.substring(replaceTo));
                        dataInserted = true;
                    } else {
                        runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                    }
                    run.setText(runText, pos);
                    globalStartIndex = globalEndIndex;
                }
            }
        }
    }

}
