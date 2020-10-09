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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
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
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.LABELS;
import static com.epam.pipeline.autotests.ao.Primitive.LABEL_INPUT_FIELD;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_ENDPOINT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
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
                entry(EXEC_ENVIRONMENT, context().find(byClassName("ant-collapse-header"))),
                entry(DEFAULT_COMMAND, context().find(byText("Cmd template:")).closest(".ant-row")
                        .find(byClassName("tools__code-editor"))),
                entry(INSTANCE, context().find(byClassName("ant-select-selection-selected-value"))),
                entry(DISK, context().find(byId("disk"))),
                entry(INSTANCE_TYPE, context().find(byText("Instance type")).closest(".ant-row")
                        .find(byClassName("ant-select-selection__rendered"))),
                entry(PRICE_TYPE, context().find(byText("Price type")).closest(".ant-row")
                        .find(byClassName("ant-select-selection__rendered"))),
                entry(SAVE, context().find(button("SAVE")))
        );
    }

    @Override
    public ToolSettings open() {
        click(SETTINGS);
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
        SelenideElement checkbox = context().$(byText("Allow sensitive storages"))
                .parent().find(By.xpath("following-sibling::div//span"));
        if (checkbox.has(cssClass("ant-checkbox-checked"))) {
            checkbox.click();
        }
        return this;
    }

    public ToolSettings enableAllowSensitiveStorage() {
        SelenideElement checkbox = context().$(byText("Allow sensitive storages"))
                .parent().find(By.xpath("following-sibling::div//span"));
        if (!checkbox.has(cssClass("ant-checkbox-checked"))) {
            checkbox.click();
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
        $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu"))).find(withText(instanceType))
                .shouldBe(visible).click();
        return this;
    }

    public ToolSettings setPriceType(final String priceType) {
        click(PRICE_TYPE);
        $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu")))
                .find(withText(priceType))
                .shouldBe(visible)
                .click();
        return this;
    }

    public ToolSettings save() {
        return sleep(1, SECONDS)
                .performIf(SAVE, enabled, settings -> settings.click(SAVE).sleep(1, SECONDS));
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
}
