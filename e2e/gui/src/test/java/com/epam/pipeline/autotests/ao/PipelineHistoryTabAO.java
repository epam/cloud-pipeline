/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.WebElement;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.COMPLETED_TIME;
import static com.epam.pipeline.autotests.ao.Primitive.HISTORY_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.LOG;
import static com.epam.pipeline.autotests.ao.Primitive.OWNER;
import static com.epam.pipeline.autotests.ao.Primitive.PIPELINE;
import static com.epam.pipeline.autotests.ao.Primitive.RERUN;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.STARTED_TIME;
import static com.epam.pipeline.autotests.ao.Primitive.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.cssSelector;
import static org.testng.Assert.assertEquals;

public class PipelineHistoryTabAO extends AbstractPipelineTabAO<PipelineHistoryTabAO> {

    private final String pipelineName;
    private Map<Primitive, SelenideElement> elements;

    public PipelineHistoryTabAO(String pipelineName) {
        super(pipelineName);
        this.pipelineName = pipelineName;
        this.elements = initialiseElements(
                super.elements(),
                entry(RERUN, $(cssSelector("[id$=rerun-button]"))),
                entry(RUN_NAME, firstRowColumns().get(1)),
                entry(PIPELINE, firstRowColumns().get(4)),
                entry(STARTED_TIME, firstRowColumns().get(6)),
                entry(COMPLETED_TIME, firstRowColumns().get(7)),
                entry(OWNER, firstRowColumns().get(9)),
                entry(STOP, $(cssSelector("[id$=stop-button]"))),
                entry(LOG, $(cssSelector("[id$=logs-button]")))
        );
    }

    @Override
    protected PipelineHistoryTabAO open() {
        changeTabTo(HISTORY_TAB);
        return this;
    }

    public PipelineHistoryTabAO recordsCountShouldBe(int size) {
        rows().shouldHaveSize(size);
        return this;
    }

    public PipelineHistoryTabAO shouldHaveEmptyHistory() {
        return ensure(byText("No data"), visible);
    }

    public PipelineHistoryTabAO shouldContainActiveRun(String pipelineName, String runId, String versionPattern, int maxElapsedTime, String owner) {
        return shouldContainRun(pipelineName, runId, versionPattern, owner)
                .ensure(STARTED_TIME, "true".equals(C.AUTH_TOKEN) ? inFormat() : inTime(maxElapsedTime))
                .ensure(COMPLETED_TIME, empty)
                .ensure(STOP, visible);
    }

    public PipelineHistoryTabAO shouldContainStoppedRun(String pipelineName, String runId, String versionPattern, int maxElapsedTime, String owner) {
        return shouldContainRun(pipelineName, runId, versionPattern, owner)
                .ensure(COMPLETED_TIME, "true".equals(C.AUTH_TOKEN) ? inFormat() : inTime(maxElapsedTime))
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

    private Condition inFormat() {
        return new Condition("not in 'yyyy-MM-dd HH:mm:ss' format") {
            @Override
            public boolean apply(WebElement element) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Utils.DATE_TIME_PATTERN);
                final LocalDateTime dateTime = LocalDateTime.parse(element.getText(), formatter);
                assertEquals(element.getText(), dateTime.format(formatter));
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
