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

package com.epam.pipeline.manager.pipeline.documents.templates.processors.base;

import lombok.Getter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Getter
public abstract class AbstractTemplateProcessor {

    private Placeholder placeholder;
    private ITemplateContext templateContext;
    private Method method;
    private Field field;

    private AbstractTemplateProcessor() {}

    public AbstractTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        this();
        this.placeholder = placeholder;
        this.templateContext = templateContext;
        this.method = method;
    }

    public AbstractTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        this();
        this.placeholder = placeholder;
        this.templateContext = templateContext;
        this.field = field;
    }

    public abstract void process(XWPFParagraph paragraph);

    protected Object getDataToInsert() {
        if (this.method != null) {
            return this.templateContext.getData(this.placeholder, this.method);
        } else if (this.field != null) {
            return this.templateContext.getData(this.placeholder, this.field);
        }
        return null;
    }
}
