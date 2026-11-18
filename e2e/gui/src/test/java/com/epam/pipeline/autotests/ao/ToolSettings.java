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
import static com.codeborne.selenide.Condition.exactValue;
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
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURE;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.DO_NOT_MOUNT_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.LABELS;
import static com.epam.pipeline.autotests.ao.Primitive.LABEL_INPUT_FIELD;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_ENDPOINT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SENSITIVE_STORAGE;
import static com.epam.pipeline.autotests.ao.Primitive.SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.tagName;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

public class ToolSettings extends ToolTab<ToolSettings> {

    // clicks on the EXECUTION ENVIRONMENT header until the section stays open (expandExecEnvironment)
    private static final int EXEC_ENVIRONMENT_CLICKS = 3;

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
                entry(SAVE, context().find(button("SAVE"))),
                entry(SENSITIVE_STORAGE, context().$(byText("Allow sensitive storages"))
                        .parent().find(By.xpath("following-sibling::div//span"))),
                entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span"))),
                entry(LIMIT_MOUNTS, context().find(byClassName("limit-mounts-input__limit-mounts-input"))),
                entry(CONFIGURE, context().find(byClassName("ant-dropdown-trigger")))
        );
    }

    @Override
    public ToolSettings open() {
        click(SETTINGS);
        return expandExecEnvironment();
    }

    /**
     * Opens the EXECUTION ENVIRONMENT section unless it is open already: a click on an open section's header would
     * close it. A new form starts with the section collapsed (openedPanels, client/.../tools/forms/EditToolForm.js),
     * and each click on SETTINGS loads the tool again and draws a new form (client/.../tools/Tool.js). The form can
     * also close the section once more when it finishes loading: EditToolForm.rebuildComponent keeps this.state,
     * awaits its requests and then sets it back, with the openedPanels it read before them. So the section has to be
     * still open a second after a click, otherwise its header is clicked again, up to EXEC_ENVIRONMENT_CLICKS times;
     * and the setters of its fields (cmd template, instance and price type, disk, sensitive storages, limit mounts)
     * call this again just before they use them.
     */
    public ToolSettings expandExecEnvironment() {
        final SelenideElement section = get(EXEC_ENVIRONMENT).shouldBe(visible).closest(".ant-collapse-item");
        for (int click = 1; !section.has(cssClass("ant-collapse-item-active")); click++) {
            if (click > EXEC_ENVIRONMENT_CLICKS) {
                section.shouldHave(cssClass("ant-collapse-item-active"));
            }
            if (click > 1) {
                System.out.printf("[WARN] EXECUTION ENVIRONMENT is closed after click %d of %d on it, clicking it again%n",
                        click - 1, EXEC_ENVIRONMENT_CLICKS);
            }
            click(EXEC_ENVIRONMENT);
            sleep(1, SECONDS);
        }
        return this;
    }

    public ToolSettings addEndpoint(final String endpoint) {
        click(NEW_ENDPOINT);
        resetMouse();
        get(PORT).find(byClassName("ant-input")).should(appear).setValue(endpoint);
        return this;
    }

    public ToolSettings addEndpoint(String endpointName, String port) {
        click(NEW_ENDPOINT);
        int endpointNum = $(byClassName("ant-form-item-control"))
                .$$(withText("Port:")).size();
        SelenideElement endpoint = $(byClassName("ant-form-item-control"))
                        .$$(withText("Port:")).get(endpointNum-1);
        endpoint.closest(".ant-row-flex-top")
            .find(byClassName("ant-input")).should(appear).setValue(port);
        endpoint.closest(".ant-row-flex-top")
                .find(byText("Name:")).parent()
                .$(byClassName("ant-input"))
                .should(appear).setValue(endpointName);
        return this;
    }

    /**
     * Replaces the port of the first endpoint. The input is a controlled one that already has the port: Selenide's
     * setValue clears only the page's text, so a redraw before the typing puts the old port back and the new one is
     * typed after it. Selecting the text and typing over it changes the value as a user does; the check stops the
     * test here if the value is still another.
     */
    public ToolSettings changeEndpointPort(final String port) {
        final SelenideElement portField = get(PORT).find(byClassName("ant-input")).should(appear);
        setValue(portField, port);
        portField.shouldHave(exactValue(port));
        return this;
    }

    /**
     * Replaces the name of the first endpoint: typed over the selected old name and checked, as in
     * {@link #changeEndpointPort(String)}.
     */
    public ToolSettings changeEndpointName(final String name) {
        final SelenideElement nameField = $(byText("Name:")).parent()
                .$(byClassName("ant-input"))
                .should(appear);
        setValue(nameField, name);
        nameField.shouldHave(exactValue(name));
        return this;
    }
    
    public ToolSettings removeEndpoint(final String endpoint) {
        $(byClassName("ant-form-item-control")).find(byValue(endpoint))
                .closest(".ant-row-flex-top").find(button("Delete")).shouldBe(visible).click();
        return this;
    }

    public ToolSettings configureEndpoint(final String configuration) {
        click(CONFIGURE);
        $(PipelineSelectors.visible(byClassName("ant-dropdown-menu-root"))).find(withText(configuration))
                .shouldBe(visible).click();
        return this;
    }

    public ToolSettings untickConfigureEndpoint(final String configuration) {
        click(CONFIGURE);
        SelenideElement option = $(PipelineSelectors.visible(byClassName("ant-dropdown-menu-root"))).find(withText(configuration))
                .shouldBe(visible);
        if(option.parent().$x("./i[@class='anticon anticon-check']").exists()) {
            option.click();
        }
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
        expandExecEnvironment();
        // in a closed section the editor stays in the page with no size, and the mouse can't be moved to it
        SelenideElement defaultCommand = get(DEFAULT_COMMAND).shouldBe(visible);
        Utils.clearTextField(defaultCommand);
        Utils.clickAndSendKeysWithSlashes(defaultCommand, command);
        return this;
    }

    public ToolSettings disableAllowSensitiveStorage() {
        expandExecEnvironment();
        if (get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
            click(SENSITIVE_STORAGE);
        }
        return this;
    }

    public ToolSettings enableAllowSensitiveStorage() {
        expandExecEnvironment();
        if (!get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
            click(SENSITIVE_STORAGE);
        }
        return this;
    }

    public ToolSettings doNotMountStoragesSelect (boolean isSelected) {
        expandExecEnvironment();
        if ((!get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
            click(DO_NOT_MOUNT_STORAGES);
        }
        return this;
    }

    public ToolSettings setDisk(final String disk) {
        return expandExecEnvironment().setValue(DISK, disk);
    }

    public ToolSettings setEndpoint(final String port) {
        return setValue(PORT, port);
    }

    public ToolSettings setInstanceType(final String instanceType) {
        expandExecEnvironment().click(INSTANCE_TYPE);
        $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu"))).find(withText(instanceType))
                .shouldBe(visible).click();
        return this;
    }

    public ToolSettings setPriceType(final String priceType) {
        expandExecEnvironment().click(PRICE_TYPE);
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
        // the editor shows a line number beside each line inside CodeMirror-code, so the command is read
        // from its first code line
        get(DEFAULT_COMMAND).find(byCssSelector(".CodeMirror-code .CodeMirror-line")).should(condition);
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

    public SelectLimitMountsPopupAO<ToolSettings> selectDataStoragesToLimitMounts() {
        expandExecEnvironment().click(LIMIT_MOUNTS);
        return  new SelectLimitMountsPopupAO<>(this).sleep(2, SECONDS);
    }

    /**
     * Limits the tool's runs to these data storages (CP_CAP_LIMIT_MOUNTS lists them), to be saved. "Do not mount
     * storages" is unticked first: the limit can't be chosen while it is ticked.
     */
    public ToolSettings limitMountsTo(final String... storages) {
        doNotMountStoragesSelect(false);
        final SelectLimitMountsPopupAO<ToolSettings> selection = selectDataStoragesToLimitMounts()
                .clearSelectionIfAny();
        Arrays.stream(storages).forEach(storage -> selection.searchStorage(storage).selectStorage(storage));
        return selection.ok();
    }
}
