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
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.WebElement;

import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.cssSelector;

public class PipelineHistoryTabAO extends AbstractPipelineTabAO<PipelineHistoryTabAO> {

    private final String pipelineName;
    private Map<Primitive, SelenideElement> elements;

    public PipelineHistoryTabAO(String pipelineName) {
        super(pipelineName);
        this.pipelineName = pipelineName;
        this.elements = initialiseElements(
                super.elements(),
                entry(RERUN, $(cssSelector("[id$=rerun-button]"))),
                entry(RUN_NAME, firstRowColumns().get(0)),
                entry(PIPELINE, firstRowColumns().get(2)),
                entry(STARTED_TIME, firstRowColumns().get(4)),
                entry(COMPLETED_TIME, firstRowColumns().get(5)),
                entry(OWNER, firstRowColumns().get(7)),
                entry(STOP, $(cssSelector("[id$=stop-button]"))),
                entry(LOG, $(cssSelector("[id$=logs-button]")))
        );
    }

    @Override
    protected PipelineHistoryTabAO open() {
        changeTabTo(HISTORY_TAB);
        return this;
    }

    public PipelineRunFormAO rerun() {
        click(RERUN);
        return new PipelineRunFormAO(pipelineName);
    }

    public PipelineHistoryTabAO recordsCountShouldBe(int size) {
        rows().shouldHaveSize(size);
        return this;
    }

    public PipelineHistoryTabAO shouldHaveEmptyHistory() {
        return ensure(byText("No Data"), visible);
    }

    public PipelineHistoryTabAO shouldContainActiveRun(String pipelineName, String runId, String versionPattern, int maxElapsedTime, String owner) {
        return shouldContainRun(pipelineName, runId, versionPattern, owner)
                .ensure(STARTED_TIME, inTime(maxElapsedTime))
                .ensure(COMPLETED_TIME, empty)
                .ensure(STOP, visible);
    }

    public PipelineHistoryTabAO shouldContainStoppedRun(String pipelineName, String runId, String versionPattern, int maxElapsedTime, String owner) {
        return shouldContainRun(pipelineName, runId, versionPattern, owner)
                .ensure(COMPLETED_TIME, inTime(maxElapsedTime))
                .ensure(RERUN, visible);
    }

    public PipelineHistoryTabAO shouldContainRunWithVersion(String version) {
        context().find(byText(version)).shouldBe(visible);
        return this;
    }

    private PipelineHistoryTabAO shouldContainRun(String pipelineName, String runId, String versionPattern, String owner) {
        return ensure(RUN_NAME, text(pipelineName.toLowerCase() + "-" + runId))
                .ensure(PIPELINE, matchText(pipelineName+"\n"+versionPattern))
                .ensure(OWNER, text(owner))
                .ensure(LOG, visible);
    }

    public PipelineHistoryTabAO viewAllVersions() {
        $(byText("View all versions history")).shouldBe(visible).click();
        sleep(2, SECONDS);
        return new PipelineHistoryTabAO(pipelineName);
    }

    private Condition inTime(int maxElapsedTime) {
        return new Condition("shorter that given period of time") {
            @Override
            public boolean apply(WebElement element) {
                Utils.assertTimePassed(element.getText(), maxElapsedTime);
                return true;
            }
        };
    }

    private ElementsCollection firstRowColumns() {
        return firstRow().findAll("td");
    }

    private SelenideElement firstRow() {
        return rows().get(0);
    }

    public ElementsCollection rows() {
        return $(".ant-table-tbody").findAll("tr");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
