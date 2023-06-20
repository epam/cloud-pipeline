/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Condition;
import static com.codeborne.selenide.Condition.have;
import com.codeborne.selenide.SelenideElement;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byValue;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;

public class ToolSettings extends ToolTab<ToolSettings> {

    private final Map<Primitive, SelenideElement> elements;

    public ToolSettings(final ToolGroup toolGroup, final String toolName) {
        super(toolGroup, toolName);
        this.elements = initialiseElements(super.elements(),
                entry(DESCRIPTION, context().find(byText("Description")).find(byId("description"))),
                entry(LABELS, context().find(button("+ New Label"))),
                entry(PORT, context().find(byText("Port:")).closest(".ant-row-flex-top")),
                entry(NEW_ENDPOINT, context().find(button("Add endpoint"))),
                entry(LABEL_INPUT_FIELD, context().find(withText("Labels")).closest(".ant-row")
                        .find(tagName("input"))),
                entry(EXEC_ENVIRONMENT, context().find(byText("EXECUTION ENVIRONMENT")).parent()),
                entry(DEFAULT_COMMAND, context().find(byText("Cmd template:")).closest(".ant-row")
                        .find(byClassName("tools__code-editor"))),
                entry(INSTANCE, context().find(byClassName("ant-select-selection-selected-value"))),
                entry(DISK, context().find(byId("disk"))),
                entry(INSTANCE_TYPE, context().find(byText("Instance type")).closest(".ant-row")
                        .find(byClassName("ant-select-selection__rendered"))),
                entry(PRICE_TYPE, context().find(byText("Price type")).closest(".ant-row")
                        .find(byClassName("ant-select-selection__rendered"))),
                entry(SAVE, context().find(button("SAVE"))),
                entry(SENSITIVE_STORAGE, context().$(byText("Allow sensitive storages"))
                        .parent().find(By.xpath("following-sibling::div//span"))),
                entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span"))),
                entry(LIMIT_MOUNTS, context().find(byClassName("limit-mounts-input__limit-mounts-input"))),
                entry(ALLOW_COMMIT, context().$(byText("Allow commit of the tool"))
                        .parent().find(By.xpath("following-sibling::div//span"))),
                entry(ADD_SYSTEM_PARAMETER, context().find(button("Add system parameters"))),
                entry(ADD_PARAMETER, context().find(byId("add-parameter-button"))),
                entry(RUN_CAPABILITIES, context().find(byXpath("//*[contains(text(), 'Run capabilities')]"))
                        .closest(".ant-row").find(className("ant-form-item-control ")))
        );
    }

    @Override
    public ToolSettings open() {
        click(SETTINGS);
        get(SETTINGS).waitUntil(have(cssClass("ant-menu-item-selected")), DEFAULT_TIMEOUT);
        get(EXEC_ENVIRONMENT).waitUntil(exist, DEFAULT_TIMEOUT);
        return click(EXEC_ENVIRONMENT);
    }

    public ToolSettings addEndpoint(final String endpoint) {
        click(NEW_ENDPOINT);
        resetMouse();
        get(PORT).find(byClassName("ant-input")).should(appear).setValue(endpoint);
        return this;
    }

    /**
     * Use {@link AccessObject#performWhile(Primitive, Condition, Consumer)} for such operations.
     */
    @Deprecated
    public ToolSettings removeEndpoint(final String endpoint) {
        get(PORT).find(byValue(endpoint)).closest("tr").find(button(endpoint)).shouldBe(visible).click();
        return this;
    }

    public ToolSettings addLabel(final String label) {
        click(LABELS);
        get(LABEL_INPUT_FIELD).setValue(label).pressEnter();
        return this;
    }

    public ToolSettings removeLabel(final String labelName) {
        context().find(label(labelName)).find(byClassName("anticon-cross")).shouldBe(visible).click();
        return this;
    }

    public ToolSettings setDefaultCommand(final String command) {
        SelenideElement defaultCommand = get(DEFAULT_COMMAND);
        Utils.clearTextField(defaultCommand);
        Utils.clickAndSendKeysWithSlashes(defaultCommand, command);
        return this;
    }

    public ToolSettings disableAllowSensitiveStorage() {
        if (get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
            click(SENSITIVE_STORAGE);
        }
        return this;
    }

    public ToolSettings enableAllowSensitiveStorage() {
        if (!get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
            click(SENSITIVE_STORAGE);
        }
        return this;
    }

    public ToolSettings doNotMountStoragesSelect (boolean isSelected) {
        if ((!get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
            click(DO_NOT_MOUNT_STORAGES);
        }
        return this;
    }

    public ToolSettings setDisk(final String disk) {
        return setValue(DISK, disk);
    }

    public ToolSettings setEndpoint(final String port) {
        return setValue(PORT, port);
    }

    public ToolSettings setInstanceType(final String instanceType) {
        click(INSTANCE_TYPE);
        $(visible(byClassName("ant-select-dropdown-menu"))).find(withText(instanceType))
                .shouldBe(visible).click();
        return this;
    }

    public ToolSettings setPriceType(final String priceType) {
        click(PRICE_TYPE);
        $(visible(byClassName("ant-select-dropdown-menu")))
                .find(withText(priceType))
                .shouldBe(visible)
                .click();
        return this;
    }

    public ToolSettings selectRunCapability(final String optionQualifier) {
        get(RUN_CAPABILITIES).shouldBe(visible).click();
        $(visible(byClassName("rc-dropdown"))).find(byText(optionQualifier))
                .shouldBe(visible).click();
        return this;
    }

    public ToolSettings save() {
        return sleep(1, SECONDS)
                .performIf(SAVE, enabled, settings -> settings.click(SAVE).sleep(1, SECONDS));
    }

    public ToolSettings allowCommit(final boolean allow) {
        if ((allow && !get(ALLOW_COMMIT).has(cssClass("ant-checkbox-checked")))
                || (!allow && get(ALLOW_COMMIT).has(cssClass("ant-checkbox-checked")))) {
            click(ALLOW_COMMIT);
        }
        return this;
    }

    private ToolSettings validateEndpoints(final Condition condition) {
        get(PORT).find(byClassName("ant-input")).should(exist, visible, condition);
        return this;
    }

    private ToolSettings validateDefaultCommand(final Condition condition) {
        get(DEFAULT_COMMAND).find(byClassName("CodeMirror-code"))
                .find(byAttribute("style", "padding-right: 0.1px;"))
                .getText();
        return this;
    }

    @Override
    public ToolSettings ensure(final Primitive primitive, final Condition... conditions) {
        switch (primitive) {
            case PORT:
                Arrays.stream(conditions).forEach(this::validateEndpoints);
                break;
            case DEFAULT_COMMAND:
                Arrays.stream(conditions).forEach(this::validateDefaultCommand);
                break;
            case INSTANCE_TYPE:
                get(INSTANCE_TYPE).getText();
                break;
            case PRICE_TYPE:
                get(PRICE_TYPE).find(byClassName("ant-select-selection-selected-value")).getText();
                break;
            default:
                super.ensure(primitive, conditions);
        }
        return this;
    }

    public PipelineRunFormAO.SystemParameterPopupAO<ToolSettings> clickSystemParameter() {
        click(ADD_SYSTEM_PARAMETER);
        return new PipelineRunFormAO.SystemParameterPopupAO<>(this);
    }

    public RunParameterAO clickCustomParameter() {
        return new PipelineRunFormAO().clickAddStringParameter();
    }

    @Override
    public SelenideElement context() {
        return $(byCssSelector(".ant-form"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static By label(final String label) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(byClassName("ant-tag")).stream()
                        .filter(webElement -> webElement.getText().contains(label))
                        .collect(toList());
            }
        };
    }

    public SelectLimitMountsPopupAO<ToolSettings> selectDataStoragesToLimitMounts() {
        click(LIMIT_MOUNTS);
        return new SelectLimitMountsPopupAO<>(this).sleep(2, SECONDS);
    }

    public ToolSettings validateDisabledParameter(final String parameter) {
        ensure(byValue(parameter), cssClass("ant-input-disabled"));
        $(byValue(parameter)).closest(".ant-row-flex").find(byId("remove-parameter-button"))
                .shouldHave(Condition.not(visible));
        return this;
    }

    public ToolSettings deleteParameter(final String parameter) {
        $(byValue(parameter)).closest(".ant-row-flex").find(byId("remove-parameter-button"))
                .shouldBe(visible)
                .click();
        return this;
    }

    public ToolSettings checkCustomCapability(final String capability, final boolean disable) {
        final SelenideElement capabilityElement = $(visible(byClassName("rc-dropdown")))
                .find(withText(capability));
        capabilityElement
                .shouldBe(visible, enabled);
        if (disable) {
            capabilityElement
                    .closest("li")
                    .shouldHave(Condition.attribute("aria-disabled", "true"))
                    .find("i")
                    .shouldHave(Condition.cssClass("anticon-question-circle-o"));
        }
        return this;
    }

    public ToolSettings checkCapabilityTooltip(final String capability, final String text) {
        $(visible(byClassName("rc-dropdown")))
                .find(withText(capability))
                .shouldBe(visible).hover();
        $(visible(byClassName("ant-tooltip")))
                        .find(byClassName("ant-tooltip-content"))
                .shouldHave(Condition.text(text));
        return this;
    }
}
