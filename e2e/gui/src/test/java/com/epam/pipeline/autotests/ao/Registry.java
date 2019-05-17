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
import org.openqa.selenium.By;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_PERSONAL_GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT_REGISTRY;
import static com.epam.pipeline.autotests.ao.Primitive.GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.GROUPS_LIST;
import static com.epam.pipeline.autotests.ao.Primitive.GROUP_SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.REGISTRY_SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.SETTINGS;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Registry implements AccessObject<Registry> {

    private final Map<Primitive, SelenideElement> elements;
    private final ToolsPage tools;

    public Registry(final ToolsPage toolsPage) {
        elements = toolsPage.elements();
        this.tools = toolsPage;
    }

    public Registry groupWithin(final String groupName, final Consumer<ToolGroup> group) {
        changeGroupTo(groupName);
        group.accept(new ToolGroup(this, groupName));
        return this;
    }

    public <PAGE> PAGE group(final String groupName, final Function<ToolGroup, PAGE> group) {
        changeGroupTo(groupName);
        return group.apply(new ToolGroup(this, groupName));
    }

    private void changeGroupTo(final String groupName) {
        sleep(1, SECONDS);
        if (!get(GROUP).getText().equals(groupName)) {
            click(GROUP);
            get(GROUPS_LIST).find(button(groupName)).shouldBe(visible).click();
            ensure(GROUP, text(groupName));
        }
    }

    public Registry edit(final Consumer<RegistryEditionPopup> registry) {
        hover(SETTINGS);
        hover(REGISTRY_SETTINGS);
        click(EDIT_REGISTRY);
        registry.accept(new RegistryEditionPopup());
        return this;
    }

    public Registry createGroup(final Consumer<ToolGroupAdditionPopup> group) {
        hover(SETTINGS).hover(GROUP_SETTINGS).click(CREATE_GROUP);
        group.accept(new ToolGroupAdditionPopup(this));
        return this;
    }

    public Registry ensureGroupsAreVisible() {
        sleep(1, SECONDS);
        click(GROUP).ensure(GROUPS_LIST, visible);
        return this;
    }

    public Registry ensureGroupAreAvailable(List<String> groupNames) {
        click(GROUP);
        sleep(1, SECONDS);
        get(GROUPS_LIST).findAll(By.className("ant-row-flex")).shouldHaveSize(1);
        groupNames.forEach(groupName ->  get(GROUPS_LIST).find(button(groupName)));
        return this;
    }

    @Override
    public SelenideElement context() {
        return tools.context();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
