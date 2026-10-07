/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;
import java.util.Map;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.RESUME;
import static com.epam.pipeline.autotests.ao.Primitive.TITLE;
import static java.lang.String.format;
import static org.openqa.selenium.By.className;
import static org.testng.Assert.assertEquals;

public class ResumePopupAO<PARENT_AO>  extends PopupAO<ResumePopupAO<PARENT_AO>, PARENT_AO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(TITLE, context().$(byClassName("esume-confirmation__title"))),
            entry(RESUME, context().find(byId("confirm-resume-run"))),
            entry(CANCEL, context().find(byId("cancel-resume-run")))
    );

    public ResumePopupAO(PARENT_AO parentAO) {
        super(parentAO);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public SelenideElement context() {
        return $$(className("ant-modal")).findBy(visible);
    }

    @Override
    public PARENT_AO cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public PARENT_AO ok() {
        return click(RESUME).parent();
    }

    public ResumePopupAO<PARENT_AO> ensureResumeTitleIs(String expectedTitle) throws RuntimeException {
        String actualTitle = get(TITLE).shouldBe(visible).find(byXpath("./span")).text();
        assertEquals(actualTitle, expectedTitle,
                format("Expected title is '%s', but actual is '%s'", expectedTitle, actualTitle));
        return this;
    }
}
