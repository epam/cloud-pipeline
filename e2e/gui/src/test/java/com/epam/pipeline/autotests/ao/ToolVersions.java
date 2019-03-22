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

import com.codeborne.selenide.Condition;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.VERSIONS;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.deleteButton;
import static com.codeborne.selenide.Condition.visible;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolVersions extends ToolTab<ToolVersions> {

    private static final Map<Primitive, By> bys;

    static {
        bys = new HashMap<>();
        bys.put(RUN, button("Run"));
        bys.put(DELETE, deleteButton());
    }

    private final By viewUnscannedVersions = button("VIEW UNSCANNED VERSIONS");

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
                        String.format("%s was not specified with selector in + %s", primitive, ToolVersions.class.getSimpleName())
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

    public ToolVersions ensureHasTag(String tagName) {
        $$(byClassName("ant-table-row")).findBy(text(tagName)).shouldBe(visible);
        return this;
    }

    public static By tags() {
        return byClassName("ant-table-row");
    }

    public RunsMenuAO runVersionWithDefaultSettings(final AbstractSeveralPipelineRunningTest test, String tool, String customTag) {
        $(byClassName("ant-table-tbody"))
                .find(byXpath(String.format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", customTag)))
                .find(byId(String.format("run-%s-button", customTag))).shouldBe(visible).click();
        new ConfirmationPopupAO<>(new RunsMenuAO())
                .ensureTitleIs(String.format("Are you sure you want to launch tool (version %s) with default settings?", customTag))
                .ok();
        sleep(1, SECONDS);
        test.addRunId(Utils.getToolRunId(tool, customTag));
        return new RunsMenuAO();
    }

    public ToolVersions deleteVersion(String customTag) {
        $(byClassName("ant-table-tbody"))
                .find(byXpath(String.format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", customTag)))
                .find(buttonByIconClass("anticon-delete")).shouldBe(visible).click();
        new ConfirmationPopupAO<>(new RunsMenuAO())
                .ensureTitleIs(String.format("Are you sure you want to delete version '%s'?", customTag))
                .ok();
        return this;
    }

    public ToolVersions viewUnscannedVersions() {
        sleep(2, SECONDS);
        if($(viewUnscannedVersions).is(exist)) {
            click(viewUnscannedVersions);
        }
        return this;
    }
}
