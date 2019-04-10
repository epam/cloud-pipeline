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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.AbstractSinglePipelineRunningTest;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;

public abstract class ToolTab<TAB extends ToolTab<TAB>> implements AccessObject<TAB> {

    private final String toolName;
    private final ToolGroup toolGroup;
    private final Map<Primitive, SelenideElement> elements;

    public ToolTab(final ToolGroup toolGroup, final String toolName) {
        this.toolGroup = toolGroup;
        final SelenideElement toolsActions = $(byClassName("tools__tool-actions"));
        final SelenideElement toolMenu = $(byClassName("tools__tool-menu"));
        final SelenideElement settingMenu = $(PipelineSelectors.visible(byClassName("ant-dropdown-menu")));
        this.elements = initialiseElements(
                entry(RUN, toolsActions.find(PipelineSelectors.button("Run"))),
                entry(RUN_DROPDOWN, toolsActions.find(byId("run-latest-menu-button"))),
                entry(TOOL_SETTINGS, toolsActions.find(byId("setting-button"))),
                entry(DELETE, settingMenu.find(byText("Delete tool"))),
                entry(PERMISSIONS, settingMenu.find(byText("Permissions"))),
                entry(SHOW_METADATA, toolsActions.find(byText("Show metadata"))),
                entry(DEFAULT_SETTINGS, $(byText("Default settings"))),
                entry(CUSTOM_SETTINGS, $(byText("Custom settings"))),
                entry(TOOL_MENU, toolMenu),
                entry(BACK_TO_GROUP, $(byClassName("tools__title")).find(tagName("button"))),
                entry(IMAGE_NAME, $(byClassName("tools__title"))),
                entry(DESCRIPTION, toolMenu.find(byText("DESCRIPTION"))),
                entry(VERSIONS, toolMenu.find(byText("VERSIONS"))),
                entry(SETTINGS, toolMenu.find(byText("SETTINGS")))
        );
        this.toolName = toolName;
    }

    public RunsMenuAO run(final AbstractSinglePipelineRunningTest test) {
        runToolWithDefaultSettings();
        test.setRunId(Utils.getToolRunId(toolName));
        return new RunsMenuAO();
    }

    public RunsMenuAO run(final AbstractSeveralPipelineRunningTest test) {
        runToolWithDefaultSettings();
        test.addRunId(Utils.getToolRunId(toolName));
        return new RunsMenuAO();
    }

    private RunsMenuAO runToolWithDefaultSettings() {
        sleep(2, SECONDS);
        click(RUN);
        return new ConfirmationPopupAO<>(new RunsMenuAO())
                .ensureTitleContains("Are you sure you want to launch tool.*with default settings?")
                .ok();
    }

    public PipelineRunFormAO runWithCustomSettings() {
        clickRun();
        if ($$(className("ant-confirm-body")).findBy(visible).isDisplayed()) {
            new ConfirmationPopupAO<>(new PipelineRunFormAO())
                .ensureTitleContains("The version has a critical number of vulnerabilities.* Run anyway?")
                .ok();
        }
        return new PipelineRunFormAO(Utils.nameWithoutGroup(toolName));
    }

    public PipelineRunFormAO runUnscannedTool(final String message) {
        clickRun();
        new ConfirmationPopupAO<>(new PipelineRunFormAO())
                .ensureTitleContains(message)
                .ok();
        return new PipelineRunFormAO(Utils.nameWithoutGroup(toolName));
    }

    private void clickRun() {
        resetMouse();
        hover(RUN_DROPDOWN);
        click(CUSTOM_SETTINGS);
        sleep(2, SECONDS);
    }

    @SuppressWarnings("unchecked")
    public TAB permissions(final Consumer<PermissionTabAO> permissions) {
        hover(TOOL_SETTINGS).click(PERMISSIONS);
        permissions.accept(new PermissionTabAO(
                () -> $(byClassName("ant-modal-close-x")).shouldBe(visible).click()
        ));
        return (TAB) this;
    }

    public <DESTINATION_TAB extends ToolTab<DESTINATION_TAB>> DESTINATION_TAB onTab(final Class<DESTINATION_TAB> tabClass) {
        Objects.requireNonNull(tabClass);
        try {
            final Constructor<DESTINATION_TAB> constructor = tabClass.getConstructor(ToolGroup.class, String.class);
            final DESTINATION_TAB tab = constructor.newInstance(toolGroup, toolName);
            tab.open();
            return tab;
        } catch (final ReflectiveOperationException exception) {
            throw new RuntimeException("Unable to instantiate ", exception);
        }
    }

    public ToolDescription description() {
        return onTab(ToolDescription.class);
    }

    public ToolVersions versions() {
        return onTab(ToolVersions.class);
    }

    public ToolSettings settings() {
        return onTab(ToolSettings.class);
    }

    public ConfirmationPopupAO<ToolGroup> delete() {
        onTab(ToolSettings.class);
        hover(TOOL_SETTINGS).click(DELETE);
        sleep(1, SECONDS);
        return new ConfirmationPopupAO<>(toolGroup);
    }

    abstract public TAB open();

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

}
