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

package com.epam.pipeline.manager.pipeline.documents.templates.processors;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholder;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.AbstractTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.ITemplateContext;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultTemplateProcessor extends AbstractTemplateProcessor {

    protected int start;
    protected int end;
    protected XWPFRun xwpfRun;

    public DefaultTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public DefaultTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    private boolean find(XWPFParagraph paragraph) {
        if (paragraph == null || paragraph.getText() == null || paragraph.getText().trim().equals("")) {
            return false;
        }
        this.start = 0;
        this.end = 0;
        this.xwpfRun = null;
        String replaceRegex = String.format("(?i)\\{%s\\}", this.getPlaceholder().regex());
        Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(paragraph.getText());
        if (matcher.find()) {
            this.start = matcher.start();
            this.end = matcher.end();
            int globalStartIndex = 0;
            int maxReplaceLength = 0;
            for (XWPFRun run : paragraph.getRuns()) {
                if (run.getText(0) == null) {
                    continue;
                }
                int globalEndIndex = globalStartIndex + run.getText(0).length();
                if (globalStartIndex > this.end || globalEndIndex < this.start) {
                    globalStartIndex = globalEndIndex;
                    continue;
                }
                int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
                int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
                if (maxReplaceLength < replaceTo - replaceFrom) {
                    maxReplaceLength = replaceTo - replaceFrom;
                    this.xwpfRun = run;
                }
                globalStartIndex = globalEndIndex;
            }
        }
        return this.xwpfRun != null;
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
        if (this.xwpfRun == null) {
            return;
        }
        int globalStartIndex = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.getText(0);
            if (runText == null) {
                continue;
            }
            int globalEndIndex = globalStartIndex + runText.length();
            if (globalStartIndex > this.end || globalEndIndex < this.start) {
                globalStartIndex = globalEndIndex;
                continue;
            }
            int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
            int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
            if (this.xwpfRun.equals(run)) {
                runText = runText.substring(0, replaceFrom)
                        .concat(data == null ? "" : data.toString())
                        .concat(runText.substring(replaceTo));
                run.setText(runText, 0);
            } else {
                runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                run.setText(runText, 0);
            }
            globalStartIndex = globalEndIndex;
        }
    }

    @Override
    public void process(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return;
        }
        Object dataToBeInserted = null;
        while (this.find(paragraph)) {
            if (dataToBeInserted == null) {
                dataToBeInserted = this.getDataToInsert();
            }
            this.replacePlaceholderWithData(paragraph, dataToBeInserted);
        }
    }
}
