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
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.disappear;
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.tagName;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

public class ToolSettings extends ToolTab<ToolSettings> {

    // clicks on the EXECUTION ENVIRONMENT header until the section stays open (expandExecEnvironment)
    private static final int EXEC_ENVIRONMENT_CLICKS = 3;
    // how long the form is waited for to get the version settings of the tool, or to end a save (waitForForm)
    private static final int FORM_WAIT_SECONDS = 30;
    // the storages the user can read, requested by the form's limit mounts input (waitForForm)
    private static final String AVAILABLE_STORAGES_REQUEST = "/datastorage/available([?#]|$)";

    // The edits made since the form was opened, for save() to make once more: see save(). An edit that adds or removes
    // something (an endpoint, a label) can't be made twice, and then none is made again.
    private final List<Consumer<ToolSettings>> edits = new ArrayList<>();
    private boolean editsRepeatable = true;
    private boolean repeatingEdits;
    private int editDepth;

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

    /**
     * Opens the tab and waits for its form to be ready for edits (waitForForm). A form the user may not edit has its
     * disk disabled from the start, and it is not waited for.
     */
    @Override
    public ToolSettings open() {
        final double opened = markResourceTimings();
        click(SETTINGS);
        expandExecEnvironment();
        if (get(DISK).is(enabled)) {
            waitForForm(opened);
        }
        return expandExecEnvironment();
    }

    /**
     * Waits for the form to be ready for edits, up to FORM_WAIT_SECONDS. Tool.js draws it before the settings of the
     * tool's default version have loaded, and a save makes it load them again; the custom parameters' "Add parameter"
     * is disabled until they are there and while a save goes on (EditToolForm: readOnly={!configuration || readOnly
     * || pending}). Then EditToolForm.rebuildComponent awaits the storages the user can read, and resets the fields
     * to the loaded values. The form's limit mounts input requests those storages again each time it is drawn
     * (LimitMountsInput.componentDidMount), and on a platform with many storages that takes seconds. So a new form,
     * opened at storagesSince (a time of the page's resource timings), is also waited for to have that request ended
     * after it; a saved form has the storages. Then two seconds more are waited, for the reset itself.
     */
    private ToolSettings waitForForm(final Double storagesSince) {
        final SelenideElement addParameter = context().find(byId("add-parameter-button"));
        final long deadline = System.currentTimeMillis() + SECONDS.toMillis(FORM_WAIT_SECONDS);
        boolean configured = addParameter.is(enabled);
        boolean storages = storagesSince == null || requestEnded(AVAILABLE_STORAGES_REQUEST, storagesSince);
        while (!(configured && storages) && System.currentTimeMillis() < deadline) {
            sleep(500, MILLISECONDS);
            configured = addParameter.is(enabled);
            storages = storages || requestEnded(AVAILABLE_STORAGES_REQUEST, storagesSince);
        }
        if (!configured || !storages) {
            System.out.printf("[WARN] The tool settings form is not ready after %d s:%s%s%n", FORM_WAIT_SECONDS,
                    configured ? "" : " \"Add parameter\" is disabled;", storages ? "" : " no storages loaded;");
        }
        return sleep(2, SECONDS);
    }

    /**
     * The time of the page's resource timings now. Their buffer is made large enough, as a page that polls the API
     * fills the default one (250 entries), and then the browser keeps no more.
     */
    private static double markResourceTimings() {
        final Number now = Selenide.executeJavaScript(
                "performance.setResourceTimingBufferSize(100000); return performance.now();");
        return now.doubleValue();
    }

    /**
     * Whether the page has a request whose URL matches the pattern that ended at since or later. One that started
     * before is counted too: the store of the storages sends no new request while one is under way (Remote.fetch).
     */
    private static boolean requestEnded(final String urlPattern, final double since) {
        return Boolean.TRUE.equals(Selenide.executeJavaScript(
                "var pattern = new RegExp(arguments[0]), since = arguments[1];"
                        + "return performance.getEntriesByType('resource').some(function (entry) {"
                        + " return entry.responseEnd >= since && pattern.test(entry.name); });",
                urlPattern, since));
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
        return edit(false, settings -> {
            click(NEW_ENDPOINT);
            resetMouse();
            get(PORT).find(byClassName("ant-input")).should(appear).setValue(endpoint);
        });
    }

    public ToolSettings addEndpoint(String endpointName, String port) {
        return edit(false, settings -> {
            click(NEW_ENDPOINT);
            int endpointNum = $(byClassName("ant-form-item-control"))
                    .$$(withText("Port:")).size();
            SelenideElement endpoint = $(byClassName("ant-form-item-control"))
                    .$$(withText("Port:")).get(endpointNum - 1);
            endpoint.closest(".ant-row-flex-top")
                    .find(byClassName("ant-input")).should(appear).setValue(port);
            endpoint.closest(".ant-row-flex-top")
                    .find(byText("Name:")).parent()
                    .$(byClassName("ant-input"))
                    .should(appear).setValue(endpointName);
        });
    }

    /**
     * Replaces the port of the first endpoint. The input is a controlled one that already has the port: Selenide's
     * setValue clears only the page's text, so a redraw before the typing puts the old port back and the new one is
     * typed after it. Selecting the text and typing over it changes the value as a user does; the check stops the
     * test here if the value is still another.
     */
    public ToolSettings changeEndpointPort(final String port) {
        return edit(true, settings -> {
            final SelenideElement portField = get(PORT).find(byClassName("ant-input")).should(appear);
            setValue(portField, port);
            portField.shouldHave(exactValue(port));
        });
    }

    /**
     * Replaces the name of the first endpoint: typed over the selected old name and checked, as in
     * {@link #changeEndpointPort(String)}.
     */
    public ToolSettings changeEndpointName(final String name) {
        return edit(true, settings -> {
            final SelenideElement nameField = $(byText("Name:")).parent()
                    .$(byClassName("ant-input"))
                    .should(appear);
            setValue(nameField, name);
            nameField.shouldHave(exactValue(name));
        });
    }
    
    public ToolSettings removeEndpoint(final String endpoint) {
        return edit(false, settings -> $(byClassName("ant-form-item-control")).find(byValue(endpoint))
                .closest(".ant-row-flex-top").find(button("Delete")).shouldBe(visible).click());
    }

    /** Ticks the option of the first endpoint's configure menu, unless it is ticked already. */
    public ToolSettings configureEndpoint(final String configuration) {
        return edit(true, settings -> settings.tickEndpointOption(configuration, true));
    }

    /** Unticks the option of the first endpoint's configure menu, unless it is unticked already. */
    public ToolSettings untickConfigureEndpoint(final String configuration) {
        return edit(true, settings -> settings.tickEndpointOption(configuration, false));
    }

    /**
     * A click on an option of the configure menu toggles it and closes the menu (client/.../tools/elements/
     * EndpointInput.js). An option that is as wanted already is not clicked, and the menu is closed by a click on its
     * link again, so that it does not take the next click.
     */
    private ToolSettings tickEndpointOption(final String configuration, final boolean ticked) {
        click(CONFIGURE);
        final SelenideElement option = $(PipelineSelectors.visible(byClassName("ant-dropdown-menu-root")))
                .find(withText(configuration))
                .shouldBe(visible);
        if (option.parent().$x("./i[@class='anticon anticon-check']").exists() != ticked) {
            option.click();
        } else {
            click(CONFIGURE);
        }
        $(PipelineSelectors.visible(byClassName("ant-dropdown-menu-root"))).should(disappear);
        return this;
    }

    public ToolSettings addLabel(final String label) {
        return edit(false, settings -> {
            click(LABELS);
            get(LABEL_INPUT_FIELD).setValue(label).pressEnter();
        });
    }

    public ToolSettings removeLabel(final String labelName) {
        return edit(false, settings ->
                context().find(label(labelName)).find(byClassName("anticon-cross")).shouldBe(visible).click());
    }

    public ToolSettings setDefaultCommand(final String command) {
        return edit(true, settings -> {
            expandExecEnvironment();
            // in a closed section the editor stays in the page with no size, and the mouse can't be moved to it
            SelenideElement defaultCommand = get(DEFAULT_COMMAND).shouldBe(visible);
            Utils.clearTextField(defaultCommand);
            Utils.clickAndSendKeysWithSlashes(defaultCommand, command);
        });
    }

    public ToolSettings disableAllowSensitiveStorage() {
        return edit(true, settings -> {
            expandExecEnvironment();
            if (get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
                click(SENSITIVE_STORAGE);
            }
        });
    }

    public ToolSettings enableAllowSensitiveStorage() {
        return edit(true, settings -> {
            expandExecEnvironment();
            if (!get(SENSITIVE_STORAGE).has(cssClass("ant-checkbox-checked"))) {
                click(SENSITIVE_STORAGE);
            }
        });
    }

    public ToolSettings doNotMountStoragesSelect(final boolean isSelected) {
        return edit(true, settings -> {
            expandExecEnvironment();
            if (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) != isSelected) {
                click(DO_NOT_MOUNT_STORAGES);
            }
        });
    }

    public ToolSettings setDisk(final String disk) {
        return edit(true, settings -> expandExecEnvironment().setValue(DISK, disk));
    }

    public ToolSettings setEndpoint(final String port) {
        return edit(true, settings -> setValue(PORT, port));
    }

    public ToolSettings setInstanceType(final String instanceType) {
        return edit(true, settings -> {
            expandExecEnvironment().click(INSTANCE_TYPE);
            $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu"))).find(withText(instanceType))
                    .shouldBe(visible).click();
        });
    }

    public ToolSettings setPriceType(final String priceType) {
        return edit(true, settings -> {
            expandExecEnvironment().click(PRICE_TYPE);
            $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu")))
                    .find(withText(priceType))
                    .shouldBe(visible)
                    .click();
        });
    }

    /**
     * Saves the edits made since the form was opened, if there is something to save (SAVE is enabled), and waits for
     * the save to end (waitForForm). The form resets its fields to the loaded values once it has them (waitForForm),
     * and when that comes after the edits, they are gone and SAVE is disabled again, as if nothing was changed. So
     * where SAVE is disabled after edits that can be made twice, they are made once more. A value that is stored
     * already leaves SAVE disabled as well, and then nothing is saved, as before.
     */
    public ToolSettings save() {
        sleep(1, SECONDS);
        if (!edits.isEmpty() && !get(SAVE).is(enabled)) {
            if (editsRepeatable) {
                System.out.printf("[WARN] SAVE of the tool settings is disabled after %d edit(s): they are stored "
                        + "already, or the form has reset them; making them again%n", edits.size());
                sleep(3, SECONDS);
                repeatingEdits = true;
                try {
                    edits.forEach(edit -> edit.accept(this));
                } finally {
                    repeatingEdits = false;
                }
                sleep(1, SECONDS);
            } else {
                System.out.printf("[WARN] SAVE of the tool settings is disabled after %d edit(s), some of which can't "
                        + "be made twice: nothing is saved%n", edits.size());
            }
        }
        edits.clear();
        editsRepeatable = true;
        return performIf(SAVE, enabled, settings -> settings.click(SAVE).sleep(1, SECONDS).waitForForm(null));
    }

    /**
     * Makes an edit and keeps it for save(), unless it is a part of another edit (limitMountsTo unticks "Do not mount
     * storages") or save() makes it again.
     */
    private ToolSettings edit(final boolean repeatable, final Consumer<ToolSettings> change) {
        editDepth++;
        try {
            change.accept(this);
        } finally {
            editDepth--;
        }
        if (editDepth == 0 && !repeatingEdits) {
            edits.add(change);
            editsRepeatable &= repeatable;
        }
        return this;
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
        return edit(true, settings -> {
            doNotMountStoragesSelect(false);
            final SelectLimitMountsPopupAO<ToolSettings> selection = selectDataStoragesToLimitMounts()
                    .clearSelectionIfAny();
            Arrays.stream(storages).forEach(storage -> selection.searchStorage(storage).selectStorage(storage));
            selection.ok();
        });
    }
}
