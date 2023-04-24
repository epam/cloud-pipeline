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
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class VSReportTemplatePageBreakProcessor implements VSReportTemplateProcessor {

    public void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                                           final GitParsedDiff diff, final GitDiffReportFilter reportFilter,
                                           final List<String> customBinaryExtension) {
        final String replaceRegex = "(?i)\\{" + template + "}";
        final Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(paragraph.getText().replace("\t", ""));
        if (matcher.find()) {
            cleanUpParagraph(paragraph);
            paragraph.setPageBreak(true);
        }
    }

}
