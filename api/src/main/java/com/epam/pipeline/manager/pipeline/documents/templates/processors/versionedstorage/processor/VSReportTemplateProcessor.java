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

import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.text.SimpleDateFormat;

public interface VSReportTemplateProcessor {

    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    default void process(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                         final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        if (paragraph == null) {
            return;
        }
        while (paragraph.getText().matches(getPlaceholderRegexp(template))) {
            replacePlaceholderWithData(paragraph, template, storage, diff, reportFilter);
        }
    }

    default String getPlaceholderRegexp(final String template) {
        return ".*(?i)\\{" + template + "}.*";
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, String template, Pipeline storage,
                                    GitParsedDiff diff, GitDiffReportFilter reportFilter);


}
