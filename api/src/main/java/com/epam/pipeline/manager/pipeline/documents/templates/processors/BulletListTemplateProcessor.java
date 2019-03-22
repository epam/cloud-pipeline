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
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

@Slf4j
public class BulletListTemplateProcessor extends SplitParagraphTemplateProcessor {

    public BulletListTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public BulletListTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    @Override
    boolean insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data == null) {
            cursor.toNextToken();
            return false;
        }
        BigInteger numberingId = this.createBulletListStyle(splittedParagraph.getDocument());
        if (data.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(data); i++) {
                this.insertDataItem(splittedParagraph, runTemplate, cursor, Array.get(data, i), numberingId);
            }
        } else if (data instanceof List<?>) {
            List<?> list = (List<?>)data;
            for (Object item : list) {
                this.insertDataItem(splittedParagraph, runTemplate, cursor, item, numberingId);
            }
        }
        return true;
    }

    void insertDataItem(XWPFParagraph splittedParagraph,
                        XWPFRun runTemplate,
                        XmlCursor cursor,
                        Object item,
                        BigInteger bulletListStyleId) {
        XWPFParagraph dataParagraph = splittedParagraph.getDocument().insertNewParagraph(cursor);
        this.copyParagraphProperties(splittedParagraph, dataParagraph);
        if (bulletListStyleId != null) {
            dataParagraph.setStyle("ListParagraph");
            dataParagraph.setNumID(bulletListStyleId);
        }
        XWPFRun newRun = dataParagraph.createRun();
        this.copyRunProperties(runTemplate, newRun);
        newRun.setText(item.toString(), 0);
        cursor.toNextToken();
    }

    private BigInteger createBulletListStyle(XWPFDocument document) {
        XWPFNumbering numbering = document.getNumbering();
        BigInteger id = BigInteger.valueOf(1);
        boolean abstractNumberingExists = true;
        while (abstractNumberingExists) {
            abstractNumberingExists = numbering.getAbstractNum(id) != null;
            if (abstractNumberingExists) {
                id = id.add(BigInteger.ONE);
            }
        }
        try {
            InputStream inputStream = getClass().getResourceAsStream("/pipeline/documents/bulletListStyle.xml");
            CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.parse(inputStream);
            XWPFAbstractNum xwpfAbstractNum = new XWPFAbstractNum(ctAbstractNum, numbering);
            xwpfAbstractNum.getAbstractNum().setAbstractNumId(id);
            id = numbering.addAbstractNum(xwpfAbstractNum);
            return document.getNumbering().addNum(id);
        } catch (XmlException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
