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

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;

public class ToolGroup implements AccessObject<ToolGroup> {

    private final Map<Primitive, SelenideElement> elements;
    public final String groupName;
    public final Registry registry;

    public ToolGroup(final Registry registry, final String groupName) {
        elements = registry.elements();
        this.registry = registry;
        this.groupName = groupName;
    }

    public ToolGroup toolWithin(final String toolName, final Consumer<ToolDescription> tool) {
        tool.andThen(backToGroup()).accept(findTool(toolName));
        return this;
    }

    private Consumer<ToolDescription> backToGroup() {
        return tool -> tool.click(BACK_TO_GROUP);
    }

    public <PAGE> PAGE tool(final String toolName, final Function<ToolDescription, PAGE> tool) {
        return tool.apply(findTool(toolName));
    }

    public ToolGroup enableTool(final String toolName) {
        hover(SETTINGS).click(ENABLE_TOOL);
        return new ToolEnablePopup(this).name(toolName).ok();
    }

    public ToolDescription findTool(final String toolName) {
        searchToolByName(toolName);
        context().find(byText(toolName)).shouldBe(visible).click();
        return new ToolDescription(this, toolName);
    }

    public ToolGroup searchToolByName(final String toolName) {
        StringSelection stringSelection = new StringSelection(toolName);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(stringSelection, null);
        SelenideElement element = get(SEARCH);
        element.click();
        element.clear();
        element.sendKeys(Keys.CONTROL + "v");
        return this;
    }

    public ToolGroup createPersonalGroup() {
        click(CREATE_PERSONAL_GROUP);
        return this;
    }

    public ToolGroup editGroup(final Consumer<ToolGroupEditionPopup> group) {
        hover(SETTINGS);
        hover(GROUP_SETTINGS);
        click(EDIT_GROUP);
        group.accept(new ToolGroupEditionPopup(this));
        return this;
    }

    public Registry deleteGroup(final Consumer<ToolGroupDeletionPopup> group) {
        hover(SETTINGS);
        hover(GROUP_SETTINGS);
        click(DELETE_GROUP);
        group.accept(new ToolGroupDeletionPopup(registry));
        return registry;
    }

    public ToolGroup canCreatePersonalGroup() {
        hover(SETTINGS);
        hover(GROUP_SETTINGS);
        ensure(CREATE_PERSONAL_GROUP_FROM_SETTINGS, visible);
        return this;
    }

    public ToolGroup assertNoToolsAreDisplayed() {
        $("tbody").shouldNotBe(visible);
        $(byClassName("ant-alert-warning")).shouldBe(visible);
        return this;
    }

    public List<String> allToolsNames() {
        // wait for all tools to load
        sleep(2, SECONDS);
        return waitUntilBodyAppears().findAll(className("tools__tool-title")).texts();
    }

    public ToolGroup ensureToolIsNotPresent(String toolName) {
        sleep(1, SECONDS);
        $(byClassName("ant-card-body")).shouldNotHave(text(toolName));
        return this;
    }

    public ToolGroup ensureToolIsPresent(String toolName) {
        sleep(1, SECONDS);
        $(byClassName("ant-card-body")).shouldHave(text(toolName));
        return this;
    }

    private SelenideElement waitUntilBodyAppears() {
        return $(byClassName("ant-card-body")).waitUntil(visible, MILLISECONDS.convert(10, SECONDS));
    }

    @Override
    public SelenideElement context() {
        return registry.context();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static By toolsNames() {
        return byClassName("tools__tool-title");
    }

    public static By tool(final String toolName) {
        return byText(toolName);
    }

    public ToolGroup ensureTextIsAbsent(String text) {
        $(byClassName("ant-card-body")).find(byText(text)).shouldNotBe(visible);
        return this;
    }
}
