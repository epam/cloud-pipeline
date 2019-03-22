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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.ElementsCollection;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static org.testng.Assert.assertThrows;

public class WDLPipelineTest extends AbstractAutoRemovingPipelineRunningTest {

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-351"})
    public void shouldCreateValidWDLPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.WDL, getPipelineName())
                .clickOnPipeline(getPipelineName());

        $(".ant-table-row").click();

        $(byText("Graph")).click();
        $(".graph__wdl-graph-container").find("svg").should(exist);

        ElementsCollection interfaceButtons = getWdlGraphIntefaceButtons();
        interfaceButtons.shouldHaveSize(3);

        interfaceButtons.get(0).find(".anticon-minus-circle-o").shouldBe(enabled);
        interfaceButtons.get(1).find(".anticon-plus-circle-o").shouldBe(enabled);
        interfaceButtons.get(2).find(".anticon-arrows-alt").shouldBe(enabled);
    }

    @Test(dependsOnMethods = {"shouldCreateValidWDLPipeline"})
    @TestCase(value = {"EPMCMBIBPC-352"})
    public void fullScreenGraphShouldBeValid() {
        ElementsCollection interfaceButtons = getWdlGraphIntefaceButtons();

        //Click full-screen button
        interfaceButtons.get(2).find("i").click();

        assertThrows(() -> $(".pipelines-library-split-pane-left").click());
        assertThrows(() -> $("#navigation-container").click());
        assertThrows(() -> $(".pipeline-details__row-menu").click());

        assertThrows(() -> $(byText("RUN")).click());
        assertThrows(() -> $(".anticon-edit").click());
        assertThrows(() -> $(byText("GIT REPOSITORY")).click());

        interfaceButtons = getWdlGraphIntefaceButtons();
        interfaceButtons.get(0).find(".anticon-minus-circle-o").shouldBe(enabled);
        interfaceButtons.get(1).find(".anticon-plus-circle-o").shouldBe(enabled);
        interfaceButtons.get(2).find(".anticon-shrink").shouldBe(enabled);
    }

    private ElementsCollection getWdlGraphIntefaceButtons() {
        return $(".graph__graph-interface").findAll(".graph__graph-interface-button");
    }

}