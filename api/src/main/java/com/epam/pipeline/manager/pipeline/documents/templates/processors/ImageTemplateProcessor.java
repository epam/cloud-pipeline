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
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Slf4j
public class ImageTemplateProcessor extends SplitParagraphTemplateProcessor {

    private static final int DEFAULT_IMAGE_WIDTH_PX = 100;
    private static final int DEFAULT_IMAGE_HEIGHT_PX = 100;

    public ImageTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public ImageTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    @Override
    boolean insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        byte[] byteArray = (byte[]) data;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
        XWPFParagraph dataParagraph = splittedParagraph.getDocument().insertNewParagraph(cursor);
        this.copyParagraphProperties(splittedParagraph, dataParagraph);
        XWPFRun newRun = dataParagraph.createRun();
        this.copyRunProperties(runTemplate, newRun);
        try {
            int width = DEFAULT_IMAGE_WIDTH_PX;
            int height = DEFAULT_IMAGE_HEIGHT_PX;
            try {
                ByteArrayInputStream imageStream = new ByteArrayInputStream(byteArray);
                BufferedImage bufferedImage = ImageIO.read(imageStream);
                width = bufferedImage.getWidth();
                height = bufferedImage.getHeight();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            double ratio = height / (width + 0.0);
            final int widthInPoints = 450;
            newRun.addPicture(inputStream,
                    XWPFDocument.PICTURE_TYPE_PNG,
                    null,
                    Units.toEMU(widthInPoints),
                    Units.toEMU(widthInPoints * ratio));
        } catch (InvalidFormatException | IOException e) {
            log.error(e.getMessage(), e);
        }
        cursor.toNextToken();
        return true;
    }
}
