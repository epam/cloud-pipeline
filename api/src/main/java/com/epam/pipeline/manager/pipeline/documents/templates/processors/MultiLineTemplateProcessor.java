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
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.ITemplateContext;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class MultiLineTemplateProcessor extends SplitParagraphTemplateProcessor {
    public MultiLineTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public MultiLineTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    @Override
    boolean insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data == null) {
            return false;
        }
        if (data.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(data); i++) {
                this.insertDataItem(splittedParagraph, runTemplate, cursor, Array.get(data, i));
            }
        } else if (data instanceof List<?>) {
            List<?> list = (List<?>)data;
            for (Object item : list) {
                this.insertDataItem(splittedParagraph, runTemplate, cursor, item);
            }
        } else {
            this.insertDataItem(splittedParagraph, runTemplate, cursor, data);
        }
        return true;
    }

    private void insertDataItem(XWPFParagraph splittedParagraph,
                                XWPFRun runTemplate,
                                XmlCursor cursor,
                                Object dataItem) {
        XWPFParagraph dataParagraph = splittedParagraph.getDocument().insertNewParagraph(cursor);
        this.copyParagraphProperties(splittedParagraph, dataParagraph);
        XWPFRun newRun = dataParagraph.createRun();
        this.copyRunProperties(runTemplate, newRun);
        newRun.setText(dataItem.toString(), 0);
        cursor.toNextToken();
    }
}
