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
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;

public class ToolsPage implements Page<ToolsPage> {

    private final Map<Primitive, SelenideElement> elements;

    public ToolsPage() {
        final Entry body = entry(BODY, $(byId("root-content")));
        final Entry header = entry(HEADER, body.getElement().find(byId("tools-header")));
        final Entry registry = entry(REGISTRY, header.getElement().find(byId("current-registry-button")));
        final Entry group = entry(GROUP, header.getElement().find(byId("current-group-button")));
        final Entry registriesList = entry(REGISTRIES_LIST, $(PipelineSelectors.visible(byClassName("tools__navigation-dropdown-container"))));
        final Entry groupsList = entry(GROUPS_LIST, $(PipelineSelectors.visible(byClassName("tools__navigation-dropdown-container"))));
        final Entry metadata = entry(SHOW_METADATA, header.getElement().find(byId("show-metadata-button")));
        final Entry settings = entry(SETTINGS, header.getElement().find(byClassName("tools__current-folder-actions")).find(byClassName("ant-dropdown-trigger")));
        final Entry registrySettings = entry(REGISTRY_SETTINGS, $(PipelineSelectors.visible(byText("Registry"))).closest(".tools__actions-sub-menu"));
        final Entry createRegistry = entry(CREATE_REGISTRY, registrySettings.getElement().find(byText("Create")));
        final Entry editRegistry = entry(EDIT_REGISTRY, registrySettings.getElement().find(byText("Edit")));
        final Entry groupSettings = entry(GROUP_SETTINGS, $(PipelineSelectors.visible(byText("Group"))).closest(".tools__actions-sub-menu"));
        final Entry editGroup = entry(EDIT_GROUP, groupSettings.getElement().find(byText("Edit")));
        final Entry deleteGroup = entry(DELETE_GROUP, groupSettings.getElement().find(byText("Delete")));
        final Entry createGroup = entry(CREATE_GROUP, groupSettings.getElement().find(byText("Create")));
        final Entry createPersonalGroupFromSettings = entry(CREATE_PERSONAL_GROUP_FROM_SETTINGS, groupSettings.getElement().find(byText("Create personal")));
        final Entry enableTool = entry(ENABLE_TOOL, $(PipelineSelectors.visible(byText("Enable tool"))));
        final Entry search = entry(SEARCH, header.getElement().find(byClassName("ant-input-search")).find(tagName("input")));
        final Entry createPersonalGroup = entry(CREATE_PERSONAL_GROUP, body.getElement().find(button("CREATE PERSONAL TOOL GROUP")));
        final Entry groupsSearch = entry(GROUPS_SEARCH, groupsList.getElement().find(tagName("input")));
        elements = initialiseElements(Page.super.elements(),
                body,
                header,
                registry,
                group,
                registriesList,
                groupsList,
                metadata,
                settings,
                registrySettings,
                createRegistry,
                editRegistry,
                groupSettings,
                editGroup,
                createGroup,
                createPersonalGroupFromSettings,
                deleteGroup,
                enableTool,
                search,
                createPersonalGroup,
                groupsSearch
        );
    }

    public ToolsPage performWithin(final String registryName,
                                   final String groupName,
                                   final String toolName,
                                   final Consumer<ToolDescription> tool
    ) {
        return registryWithin(registryName, registry ->
                registry.groupWithin(groupName, group ->
                        group.toolWithin(toolName, tool)
                )
        );
    }

    public ToolsPage performWithin(final String registryName,
                                   final String groupName,
                                   final Consumer<ToolGroup> group
    ) {
        return registryWithin(registryName, registry ->
                registry.groupWithin(groupName, group)
        );
    }

    public <PAGE> PAGE perform(final String registryName,
                               final String groupName,
                               final String toolName,
                               final Function<ToolDescription, PAGE> tool
    ) {
        return registry(registryName, registry ->
                registry.group(groupName, group ->
                        group.tool(toolName, tool)
                )
        );
    }

    public <PAGE> PAGE perform(final String registryName,
                               final String groupName,
                               final Function<ToolGroup, PAGE> group
    ) {
        return registry(registryName, registry ->
                registry.group(groupName, group)
        );
    }

    public ToolsPage registryWithin(final String registryName, final Consumer<Registry> registry) {
        changeRegistryTo(registryName);
        registry.accept(new Registry(this));
        return this;
    }

    public <PAGE> PAGE registry(final String registryName, final Function<Registry, PAGE> registry) {
        changeRegistryTo(registryName);
        return registry.apply(new Registry(this));
    }

    public ToolsPage editRegistry(final String registryName, final Consumer<RegistryEditionPopup> reg) {
        registryWithin(registryName, registry -> registry.edit(reg));
        return this;
    }

    private void changeRegistryTo(final String registryName) {
        sleep(1, SECONDS);
        if (!get(REGISTRY).getText().equals(registryName)) {
            click(REGISTRY);
            get(REGISTRIES_LIST).find(button(registryName)).shouldBe(visible).click();
            ensure(REGISTRY, text(registryName));
        }
    }

    public ToolsPage ensureRegistrySelected(String registry) {
        ensure(REGISTRY, text(registry));
        return this;
    }

    public ToolsPage ensureOnlyOneRegistryIsAvailable() {
        click(REGISTRY);
        sleep(1, SECONDS);
        $(byId("registries-dropdown")).shouldNotBe(visible);
        return this;
    }

    public ToolsPage addRegistry(final Consumer<RegistryAdditionPopup> registry) {
        hover(SETTINGS);
        hover(REGISTRY_SETTINGS);
        click(CREATE_REGISTRY);
        registry.accept(new RegistryAdditionPopup());
        return this;
    }

    public ToolsPage deleteRegistry(final String registryName,
                                    final Consumer<ConfirmationPopupAO<RegistryEditionPopup>> registry
    ) {
        changeRegistryTo(registryName);
        hover(SETTINGS);
        hover(REGISTRY_SETTINGS);
        click(EDIT_REGISTRY);
        registry.accept(new RegistryEditionPopup().deleteRegistry());
        return this;
    }

    public ToolsPage ensureOnlyRegistryPresentIs(String registryPath) {
        $(".ant-select-selection__rendered").doubleClick();
        $(byClassName("ant-select-dropdown-menu"))
                .findAll(".ant-select-dropdown-menu-item")
                .shouldHaveSize(1);
        return this.ensureRegistryIsPresent(registryPath);
    }

    public ToolsPage ensureRegistryIsPresent(String registryPath) {
        click(REGISTRY);
        ensure(REGISTRIES_LIST, visible);
        get(REGISTRIES_LIST).shouldHave(text(registryPath));
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public SelenideElement context() {
        return get(BODY);
    }

    public ToolsPage select(final String registryName) {
        return registryWithin(registryName, registry -> {});
    }

    public ToolsPage select(final String registryName, final String groupName) {
        return performWithin(registryName, groupName, group -> {});
    }
}
