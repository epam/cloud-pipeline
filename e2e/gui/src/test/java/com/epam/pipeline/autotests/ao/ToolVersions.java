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
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.Color;
import org.openqa.selenium.support.Colors;
import org.testng.collections.CollectionUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.codeborne.selenide.CollectionCondition.texts;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.ARROW;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.VERSIONS;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.deleteButton;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

public class ToolVersions extends ToolTab<ToolVersions> {

    private static final Color SEARCH_COLOR = Colors.YELLOW.getColorValue();
    private static final Map<Primitive, By> bys;

    static {
        bys = new HashMap<>();
        bys.put(RUN, button("Run"));
        bys.put(DELETE, deleteButton());
        bys.put(ARROW, buttonByIconClass("anticon-arrow-left"));
    }

    private final Condition tableIsEmpty = new Condition("table is empty") {
        @Override
        public boolean apply(final WebElement ignored) {
            return $(byClassName("ant-table-placeholder")).is(visible);
        }
    };

    private final By viewUnscannedVersions = button("VIEW UNSCANNED VERSIONS");

    private final By hideUnscannedVersions = button("HIDE UNSCANNED VERSION");

    private final By scan = button("SCAN");
    private final String[] severity = new String[]{"Critical", "High", "Medium", "Low", "Negligible"};

    private By scanComponent(final String component) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $(".ant-table-body")
                        .findAll(".ant-table-row").stream()
                        .filter(element -> text(component).apply(element))
                        .collect(toList());
            }
        };
    }

    public ToolVersions(final ToolGroup toolGroup, final String toolName) {
        super(toolGroup, toolName);
    }

    @Override
    public ToolVersions open() {
        return click(VERSIONS);
    }

    public static By byPrimitive(final Primitive primitive) {
        return Optional.ofNullable(bys.get(primitive))
                .orElseThrow(() -> new RuntimeException(
                        String.format("%s was not specified with selector in + %s", primitive,
                                ToolVersions.class.getSimpleName())
                ));
    }

    public static Consumer<ToolVersions> tagsHave(final Primitive... primitives) {
        final Condition containsAllPrimitives = new Condition("Element contains all primitives ") {
            @Override
            public boolean apply(final WebElement element) {
                return Arrays.stream(primitives).allMatch(existsFor(element));
            }

            private Predicate<Primitive> existsFor(final WebElement element) {
                return primitive -> !element.findElements(byPrimitive(primitive)).isEmpty();
            }
        };

        return toolVersions -> toolVersions.ensureAll(tags(), containsAllPrimitives);
    }

    public static By versionTab(final String tab) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $(".ant-tabs")
                        .findAll(".ant-tabs-tab").stream()
                        .filter(element -> text(tab).apply(element))
                        .collect(toList());
            }
        };
    }

    public static By toolVersion(final String version) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $(".ant-table-body")
                        .findAll(".ant-table-row").stream()
                        .filter(element -> text(version).apply(element))
                        .collect(toList());
            }
        };
    }

    public ToolVersions ensureHasTag(String tagName) {
        $$(byClassName("ant-table-row")).findBy(text(tagName)).shouldBe(visible);
        return this;
    }

    public static By tags() {
        return byClassName("ant-table-row");
    }

    public RunsMenuAO runVersionWithDefaultSettings(final AbstractSeveralPipelineRunningTest test,
                                                    final String tool,
                                                    final String customTag) {
        $(byClassName("ant-table-tbody"))
                .find(byXpath(String.format(
                        ".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", customTag)))
                .find(byId(String.format("run-%s-button", customTag))).shouldBe(visible).click();
        new RunsMenuAO()
                .messageShouldAppear(String.format(
                        "Are you sure you want to launch tool (version %s) with default settings?", customTag))
                .click(button("Launch"));
        sleep(1, SECONDS);
        test.addRunId(Utils.getToolRunId(tool, customTag));
        return new RunsMenuAO();
    }

    public ToolVersions deleteVersion(String customTag) {
        $(byClassName("ant-table-tbody"))
                .find(byXpath(String.format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]",
                        customTag)))
                .find(buttonByIconClass("anticon-delete")).shouldBe(visible).click();
        new ConfirmationPopupAO<>(new RunsMenuAO())
                .messageShouldAppear(String.format("Are you sure you want to delete version '%s'?", customTag))
                .delete();
        return this;
    }

    public PipelineRunFormAO runVersion(final String version) {
        actions().click($(toolVersion(version)).find(byId(String.format("run-%s-button", version)))).perform();
        return new PipelineRunFormAO();
    }

    public ToolVersions viewUnscannedVersions() {
        sleep(2, SECONDS);
        if($(viewUnscannedVersions).exists()) {
            click(viewUnscannedVersions);
        }
        return this;
    }

    public ToolVersions viewUnscannedVersionsAvailable() {
        $(viewUnscannedVersions).shouldBe(enabled);
        return this;
    }

    public ToolVersions versionTableShouldBeEmpty() {
        $(byClassName("ant-table-tbody")).should(tableIsEmpty);
        return this;
    }

    public ToolVersions validateUnscannedVersionsPage() {
        $(byClassName("tools__version-scanning-info")).shouldHave(text("Version was not scanned"));
        $(toolVersion("")).find(buttonByIconClass("anticon-exclamation-circle")).shouldBe(visible);
        $(viewUnscannedVersions).is(hidden);
        $(hideUnscannedVersions).is(enabled);
        return this;
    }

    public ToolVersions validateScannedVersionsPage() {
        $(byClassName("tools__version-scanning-info")).shouldHave(matchText("Successfully scanned at .*"));
        $(toolVersion("")).find(buttonByIconClass("anticon-exclamation-circle")).shouldBe(hidden);
        $(".tools__version-scanning-info-graph").is(visible);
        return this;
    }

    public ToolVersions selectVersion(final String version) {
        sleep(1, SECONDS);
        $(toolVersion(version)).click();
        return this;
    }

    public ToolVersions validateVersionPage(final String activeTab) {
        $(versionTab("VULNERABILITIES REPORT")).shouldBe(visible);
        $(versionTab("SETTINGS")).shouldBe(visible);
        $(versionTab("PACKAGES")).shouldBe(visible);
        $(versionTab(activeTab)).shouldHave(cssClass("ant-tabs-tab-active"));
        return this;
    }

    public ToolVersions validateReportTableColumns() {
        $(byClassName("ant-table-body")).findAll("th").shouldHave(texts("Component", "Severity"));
        return this;
    }

    public ToolVersions validateEcosystem(final List<String> fields) {
        clickToEcosystem();
        fields.forEach(field ->
                $(byClassName("ant-select-dropdown-menu"))
                        .findAll(byClassName("ant-select-dropdown-menu-item"))
                        .find(text(field))
                        .shouldBe(exist)
        );
        return this;
    }

    public ToolVersions selectEcosystem(final String field) {
        clickToEcosystem();
        $(byClassName("ant-select-dropdown-menu"))
                .findAll(byClassName("ant-select-dropdown-menu-item"))
                .find(text(field))
                .click();
        return this;
    }

    private ToolVersions clickToEcosystem() {
        $(byText("Ecosystem:"))
                .closest(".ant-row-flex")
                .find(".ant-select")
                .click();
        return this;
    }

    public ToolVersions scanVersion(final String version) {
        $(toolVersion(version)).find(scan).click();
        return this;
    }

    public ToolVersions validateScanningProcess(final String version) {
        $(toolVersion(version)).find(byClassName("anticon-loading")).should(exist);
        $(toolVersion(version)).find(byText("SCANNING")).should(visible);
        $(toolVersion(version)).find(scan).waitUntil(visible, 120000);
        return this;
    }

    public ToolVersions arrow() {
        click(buttonByIconClass("anticon-arrow-left"));
        return this;
    }

    public ToolVersions checkDiagram() {
       hover(byClassName("version-scan-result__container"));
       Stream.of(severity).forEach(e -> ensure(byText(e), visible));
       return this;
    }

    public ToolVersions selectComponent(final String component) {
        final SelenideElement scanComponent = $(scanComponent(component));
        scanComponent.find(byClassName("ant-table-row-expand-icon")).click();
        final ElementsCollection advisors = $$(".tool-scanning-info__vulnerability-row").filterBy(visible);
        advisors.forEach(a -> {
            a.find("a").shouldHave(attribute("href")).shouldHave(text("RHSA-"));
            a.shouldHave(Condition.or("severity",
                    text(severity[0]), text(severity[1]), text(severity[2]), text(severity[3]), text(severity[4])));
        });
        return this;
    }

    public ToolVersions validatePackageList(final List<String> packages, final boolean description) {
        if (!CollectionUtils.hasElements(packages)) {
            $(byClassName("ackages__dependency")).shouldBe(hidden);
        } else {
            packages.forEach(p -> {
                dependency(p).shouldBe(visible);
                if (description) {
                    dependency(p).find(byClassName("ackages__description")).shouldBe(exist);
                }
            });
        }
        return this;
    }

    public ToolVersions filterPackages(final String value) {
        $$("input")
                .findBy(attribute("placeholder", "Filter dependencies"))
                .setValue(value);
        return this;
    }

    public ToolVersions validateSearchResult(final String value) {
        dependency(value).find(byText(value)).shouldHave(backgroundColor(SEARCH_COLOR));
        return this;
    }

    public static SelenideElement dependency(final String pack) {
        return $$(byClassName("ackages__dependency")).find(text(pack));
    }

    public ToolVersions addToWhiteList(final String version) {
        $(toolVersion(version)).find(button("Add to white list")).click();
        $(toolVersion(version)).find(button("Remove from white list")).should(visible);
        $(toolVersion(version)).find(button("Add to white list")).should(hidden);
        return this;
    }
}
