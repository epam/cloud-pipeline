/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.utils.C;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.switchTo;
import static com.codeborne.selenide.Selenide.title;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.epam.pipeline.autotests.ao.Primitive.TITLE;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class ToolPageAO implements AccessObject<ToolPageAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(TITLE, context().find(byClassName("tools__tools-header")))
    );

    protected String endpoint;

    public ToolPageAO(final String endpoint) {
        super();
        this.endpoint = endpoint;
    }

    public ToolPageAO assertPageTitleIs(final String expectedTitle) {
        sleep(3, SECONDS);
        if (!title().contains(expectedTitle)) {
            screenshot("page-title");
            throw new RuntimeException("Page title is not the same as expected");
        }
        return this;
    }

    public ToolPageAO assertPageContains(String text) {
        $(withText(text)).shouldBe(visible);
        return this;
    }

    public ToolPageAO validateEndpointPage(final String owner) {
        $(byId("owner")).should(appear).shouldHave(text(owner));
        return this;
    }

    public ToolPageAO validateAliveWorkersSparkPage(String num) {
        $(withText("Alive Workers:")).closest("li").should(appear).shouldHave(text(num));
        return this;
    }

    public ToolPageAO validationPageHeader(String header) {
        $(byXpath("//h3")).shouldHave(text(header));
        return this;
    }

    public ToolPageAO assertURLEndsWith(String str) {
        assertTrue(WebDriverRunner.getWebDriver().getCurrentUrl().endsWith(str + "/"),
                format("Link from the address bar doesn't end by the '%s'", str));
        return this;
    }


    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void closeTab() {
        List<String> tabs = new ArrayList<>(getWebDriver().getWindowHandles());
        getWebDriver().close();
        switchTo().window(tabs.get(0));
    }
}
