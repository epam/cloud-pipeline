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

import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.AbstractTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.ITemplateContext;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholder;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholders;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Data
@Slf4j
public class GeneralTemplate implements ITemplateContext {

    protected Map<Placeholder, Object> dataValues;
    protected List<AbstractTemplateProcessor> processors;
    protected List<DocumentGenerationProperty> documentGenerationProperties;

    GeneralTemplate() {
        this.processors = new ArrayList<>();
        Method[] methods = this.getClass().getMethods();
        Field[] fields = this.getClass().getFields();
        Field[] declaredFields = this.getClass().getDeclaredFields();
        for (Method method : methods) {
            Placeholders placeholders = method.getAnnotation(Placeholders.class);
            if (placeholders != null) {
                for (Placeholder placeholder : placeholders.value()) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Method.class);
                        this.processors.add(constructor.newInstance(placeholder, this, method));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            } else {
                Placeholder placeholder = method.getAnnotation(Placeholder.class);
                if (placeholder != null) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Method.class);
                        this.processors.add(constructor.newInstance(placeholder, this, method));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
        for (Field field : fields) {
            Placeholders placeholders = field.getAnnotation(Placeholders.class);
            if (placeholders != null) {
                for (Placeholder placeholder : placeholders.value()) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Field.class);
                        this.processors.add(constructor.newInstance(placeholder, this, field));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            } else {
                Placeholder placeholder = field.getAnnotation(Placeholder.class);
                if (placeholder != null) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Field.class);
                        this.processors.add(constructor.newInstance(placeholder, this, field));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
        for (Field field : declaredFields) {
            Placeholders placeholders = field.getAnnotation(Placeholders.class);
            if (placeholders != null) {
                for (Placeholder placeholder : placeholders.value()) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Field.class);
                        this.processors.add(constructor.newInstance(placeholder, this, field));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            } else {
                Placeholder placeholder = field.getAnnotation(Placeholder.class);
                if (placeholder != null) {
                    try {
                        Constructor<? extends AbstractTemplateProcessor> constructor = placeholder.processor()
                                .getConstructor(Placeholder.class, ITemplateContext.class, Field.class);
                        this.processors.add(constructor.newInstance(placeholder, this, field));
                    } catch (NoSuchMethodException |
                            IllegalAccessException |
                            InvocationTargetException |
                            InstantiationException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
    }

    void applyDocumentTemplateProperties() {
        if (this.documentGenerationProperties != null) {
            for (DocumentGenerationProperty property : this.documentGenerationProperties) {
                Optional<AbstractTemplateProcessor> abstractTemplateProcessorOptional =
                        this.processors.stream().filter(p -> p.getPlaceholder().templateProperty()
                                .equals(property.getPropertyName())).findAny();
                if (abstractTemplateProcessorOptional.isPresent()) {
                    AbstractTemplateProcessor processor = abstractTemplateProcessorOptional.get();
                    if (processor.getField() == null) {
                        continue;
                    }
                    Object value = property.getPropertyValue();
                    if (processor.getPlaceholder().templatePropertyIsBinary()) {
                        value = new byte[0];
                        if (property.getPropertyValue() != null && !property.getPropertyValue().equals("")) {
                            value = org.apache.commons.codec.binary.
                                    Base64.decodeBase64(property.getPropertyValue());
                        }
                    }
                    try {
                        processor.getField().set(this, value);
                    } catch (IllegalAccessException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
    }

    public void fillTemplate(XWPFDocument docxTemplate) {
        this.changeHeadersAndFooters(docxTemplate);
        this.changeBodyElements(docxTemplate::getBodyElements);
    }

    private void changeHeadersAndFooters(XWPFDocument document) {
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        this.changeHeader(policy.getDefaultHeader());
        this.changeFooter(policy.getDefaultFooter());
        this.changeHeader(policy.getEvenPageHeader());
        this.changeFooter(policy.getEvenPageFooter());
        this.changeHeader(policy.getOddPageHeader());
        this.changeFooter(policy.getEvenPageFooter());
        for (XWPFHeader header : document.getHeaderList()) {
            this.changeHeader(header);
        }
    }

    private void changeHeader(XWPFHeader header) {
        if (header == null) {
            return;
        }
        this.changeBodyElements(header::getBodyElements);
    }

    private void changeFooter(XWPFFooter footer) {
        if (footer == null) {
            return;
        }
        this.changeBodyElements(footer::getBodyElements);
    }

    /**
     * Modifies elements. Replaces all occurrences of placeholders with corresponding values
     *
     * @param getBodyElements is supplier which returns list of elements
     */
    private void changeBodyElements(Supplier<List<IBodyElement>> getBodyElements) {
        int size = getBodyElements.get().size();
        for (int i = 0; i < size; i++) {
            this.changeBodyElement(getBodyElements.get().get(i));
            size = getBodyElements.get().size();
        }
    }

    private void changeBodyElement(IBodyElement element) {
        switch (element.getElementType()) {
            case TABLE:
                this.changeTable((XWPFTable) element);
                break;
            case PARAGRAPH:
                this.changeParagraph((XWPFParagraph) element);
                break;
            default:
                break;
        }
    }

    /**
     * Modifies word document's paragraph. Replaces all occurrences of placeholders with corresponding values
     *
     * @param paragraph paragraph to be modified
     */
    private void changeParagraph(XWPFParagraph paragraph) {
        for (AbstractTemplateProcessor processor : this.processors) {
            processor.process(paragraph);
        }
    }

    /**
     * Modifies word document's table. Replaces all occurrences of placeholders with corresponding values
     *
     * @param table XWPFTable to be modified
     */
    private void changeTable(XWPFTable table) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                this.changeBodyElements(cell::getBodyElements);
            }
        }
    }

    @Override
    public Object getData(Placeholder part, Method method) {
        if (this.dataValues == null) {
            this.dataValues = new HashMap<>();
        }
        if (!this.dataValues.containsKey(part)) {
            try {
                this.dataValues.put(part, this.getTemplateValue(method.invoke(this), null, part));
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error(e.getMessage(), e);
                this.dataValues.put(part, null);
            }
        }
        return this.dataValues.get(part);
    }

    @Override
    public Object getData(Placeholder part, Field field) {
        if (this.dataValues == null) {
            this.dataValues = new HashMap<>();
        }
        if (!this.dataValues.containsKey(part)) {
            try {
                this.dataValues.put(part, this.getTemplateValue(field.get(this), field.getType(), part));
            } catch (IllegalAccessException e) {
                log.error(e.getMessage(), e);
                this.dataValues.put(part, null);
            }
        }
        return this.dataValues.get(part);
    }

    private Object getTemplateValue(Object objectValue, Class<?> objectClass, Placeholder placeholder) {
        try {
            if (objectValue != null && objectClass != null && !placeholder.methodName().equals("")) {
                Method method = objectClass.getMethod(placeholder.methodName());
                if (method != null) {
                    return method.invoke(objectValue);
                } else {
                    return objectClass;
                }
            } else {
                return objectValue;
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
