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

package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractVSReportTemplateProcessor implements VSReportTemplateProcessor {

    protected final ReportDataExtractor dataProducer;

    protected int start;
    protected int end;
    protected XWPFRun xwpfRun;
    protected int pos = -1;

    public AbstractVSReportTemplateProcessor(ReportDataExtractor dataProducer) {
        this.dataProducer = dataProducer;
    }

    @Override
    public void process(XWPFParagraph paragraph, String template, Pipeline storage, GitDiff diff,
                        GitDiffReportFilter reportFilter) {
        if (paragraph == null) {
            return;
        }
        while (this.find(paragraph, template)) {
            this.replacePlaceholderWithData(paragraph, dataProducer.apply(paragraph, storage, diff, reportFilter));
        }
    }

    boolean find(XWPFParagraph paragraph, String template) {
        if (paragraph == null || paragraph.getText() == null || paragraph.getText().trim().equals("")) {
            return false;
        }
        this.start = 0;
        this.end = 0;
        this.xwpfRun = null;
        String replaceRegex = "(?i)\\{" + template + "}";
        Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(paragraph.getText().replace("\t", ""));
        if (matcher.find()) {
            this.start = matcher.start();
            this.end = matcher.end();
            int globalStartIndex = 0;
            int maxReplaceLength = 0;
            for (XWPFRun run : paragraph.getRuns()) {
                for (int pos = 0; pos < run.getCTR().sizeOfTArray(); pos++) {
                    if (run.getText(pos) == null) {
                        continue;
                    }
                    int globalEndIndex = globalStartIndex + run.getText(pos).length();
                    if (globalStartIndex > this.end || globalEndIndex < this.start) {
                        globalStartIndex = globalEndIndex;
                        continue;
                    }
                    int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
                    int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
                    if (maxReplaceLength < replaceTo - replaceFrom) {
                        maxReplaceLength = replaceTo - replaceFrom;
                        this.xwpfRun = run;
                        this.pos = pos;
                    }
                    globalStartIndex = globalEndIndex;
                }
            }
        }
        return this.xwpfRun != null;
    }

    abstract void replacePlaceholderWithData(XWPFParagraph paragraph, Object data);

}
