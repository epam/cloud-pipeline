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
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

@RequiredArgsConstructor
public abstract class AbstractVSReportTemplateProcessor implements VSReportTemplateProcessor {

    protected final ReportDataExtractor dataProducer;

    @Override
    public void process(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                        final GitDiff diff, final GitDiffReportFilter reportFilter) {
        if (paragraph == null) {
            return;
        }
        while (paragraph.getText().matches(getPlaceholderRegexp(template))) {
            replacePlaceholderWithData(
                    paragraph, template, dataProducer.apply(paragraph, storage, diff, reportFilter)
            );
        }
    }

    abstract void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Object data);

    private String getPlaceholderRegexp(final String template) {
        return ".*(?i)\\{" + template + "}.*";
    }

}
