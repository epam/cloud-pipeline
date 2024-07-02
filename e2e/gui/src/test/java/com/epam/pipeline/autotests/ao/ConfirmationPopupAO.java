/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import java.util.function.Consumer;

import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static java.lang.String.format;
import static java.lang.String.join;
import static org.openqa.selenium.By.className;
import static org.testng.Assert.assertEquals;

public class ConfirmationPopupAO<PARENT_AO> extends PopupAO<ConfirmationPopupAO<PARENT_AO>, PARENT_AO> {
    private final SelenideElement element = $$(className("ant-confirm")).findBy(visible);
    private final SelenideElement title = element.find(className("ant-confirm-title"));
    private boolean messageIsChecked = false;
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(OK, $(byXpath("//*[contains(@role, 'dialog') and .//*[contains(@class, 'ant-confirm')]]//button[. =  'OK' or . = 'Yes' or . = 'Launch']"))),
            entry(CANCEL, $(byXpath("//*[contains(@role, 'dialog') and .//*[contains(@class, 'ant-confirm')]]//button[. =  'Cancel' or . = 'No']"))),
            entry(DELETE, $(byId("remove-button-delete")))
    );

    public ConfirmationPopupAO(PARENT_AO parentAO) {
        super(parentAO);
    }

    @Override
    public SelenideElement context() {
        return $(byXpath("//*[contains(@role, 'dialog') and .//*[contains(@class, 'ant-confirm')]]"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public PARENT_AO ok() {
        if (!messageIsChecked) {
            throw new RuntimeException(format(
                    "You have tried to confirm something without checking the title that is: \"%s\"", title.text()
            ));
        }
        click(OK);
        return parent();
    }

    @Override
    public PARENT_AO cancel() {
        click(CANCEL);
        return super.parent();
    }

    @Override
    public void closeAll() {
        cancel();
    }

    public PARENT_AO delete() {
        click(DELETE);
        return parent();
    }

    public ConfirmationPopupAO<PARENT_AO> ensureTitleIs(String expectedTitle) throws RuntimeException {
        title.shouldHave(text(expectedTitle));
        this.messageIsChecked = true;
        return this;
    }

    public ConfirmationPopupAO<PARENT_AO> ensureTitleContains(String expectedTitle) throws RuntimeException {
        title.should(matchText(expectedTitle));
        this.messageIsChecked = true;
        return this;
    }

    public ConfirmationPopupAO<PARENT_AO> ensureLaunchTitleIs(String expectedTitle) throws RuntimeException {
        String actualTitle = join(" ", element.find(className("cp-run-name-title"))
                .findAll(byXpath("./span")).texts());
        assertEquals(actualTitle, expectedTitle,
                format("Expected title is '%s', but actual is '%s'", expectedTitle, actualTitle));
        this.messageIsChecked = true;
        return this;
    }

    public static Consumer<ConfirmationPopupAO<LogAO>> confirmCommittingToExistingTool(final String registryIp,
                                                                                       final String tool
    ) {
        return log -> log.ensureTitleIs(format("%s/%s already exists. Overwrite?", registryIp, tool)).ok();
    }

    public static Consumer<ConfirmationPopupAO<LogAO>> confirmCommittingToExistingTool(final String registryIp,
                                                                                       final String tool,
                                                                                       final String version
    ) {
        return log -> log.ensureTitleIs(format("%s/%s:%s already exists. Overwrite?", registryIp, tool, version)).ok();
    }
}
