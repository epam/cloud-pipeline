/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.ElementNotFound;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Conditions;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.codeborne.selenide.CollectionCondition.empty;
import static com.codeborne.selenide.CollectionCondition.*;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.utils.C.COMPLETION_TIMEOUT;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.elementWithText;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertTrue;

public class RunsMenuAO implements AccessObject<RunsMenuAO> {

    private static final String GET_LOGS_ERROR = "get_logs_error";
    private static final long APPEARING_TIMEOUT = C.SSH_APPEARING_TIMEOUT;

    private final Condition tableIsEmpty = new Condition("table is empty") {
        @Override
        public boolean apply(final WebElement ignored) {
            return $(byClassName("ant-table-placeholder")).is(visible);
        }
    };

    public static By runOf(final String pipelineName) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $("tbody")
                        .findAll("tr").stream()
                        .filter(element -> text(pipelineName).apply(element))
                        .collect(toList());
            }
        };
    }

    public RunsMenuAO activeRuns() {
        $(byId("active-runs-button")).shouldBe(visible).click();
        tableShouldAppear();
        return new RunsMenuAO();
    }

    public RunsMenuAO completedRuns() {
        $(byId("completed-runs-button")).shouldBe(visible).click();
        tableShouldAppear();
        return new RunsMenuAO();
    }

    public RunsMenuAO nextPageCompletedRuns() {
        if ($(byClassName(" ant-pagination-next")).attr("aria-disabled").equals("false")) {
            $(byClassName(" ant-pagination-next")).click();
            tableShouldAppear();
        }
        return new RunsMenuAO();
    }

    public RunsMenuAO switchAllPagesCompletedRuns() {
        while ($(byClassName(" ant-pagination-next")).attr("aria-disabled").equals("false")) {
            nextPageCompletedRuns();
        }
        return new RunsMenuAO();
    }

    public RunsMenuAO stopRun(String runId) {
        final SelenideElement runStopButton = $("#run-" + runId + "-stop-button");
        runStopButton.waitUntil(enabled, 5000).click();
        sleep(3, SECONDS);
        if (!$(button("STOP")).isEnabled()) {
            runStopButton.waitUntil(enabled, 5000).click();
        }
        $(button("STOP")).click();
        return this;
    }

    public RunsMenuAO show(String runId) {
        $("#run-" + runId + "-logs-button").should(appear).click();
        return this;
    }

    public LogAO showLog(String runId) {
        sleep(1, SECONDS);
        show(runId);
        sleep(3, SECONDS);
        if (new LogAO().get(STATUS).exists()) {
            return new LogAO();
        }
        show(runId);
        return new LogAO();
    }

    public LogAO showLogForChildRun(String runId) {
        return showLogForChildRun(runId, 0);
    }

    public LogAO showLogForChildRun(String runId, int index) {
        sleep(1, SECONDS);
        $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(text(runId))
                .get(index)
                .click();
        return new LogAO();
    }

    public RunsMenuAO log(final String runId, final Consumer<LogAO> log) {
        log.accept(showLog(runId));
        return new NavigationMenuAO().runs();
    }

    /**
     * Will click several times to be sure logs are shown
     */
    public LogAO showLogForce(String runId) {
        show(runId);
        sleep(2, SECONDS);

        int trys = 0;
        while (!$(byClassName("log__run-title")).exists()) {
            System.out.println("[WARN] retrying click run logs");
            if (trys++ > 5) {
                Selenide.screenshot(GET_LOGS_ERROR);
                throw new ElementNotFound(format("Could not get run logs (screenshot: %s.png)", GET_LOGS_ERROR), exist);
            }

            show(runId);
            sleep(2, SECONDS);
        }

        return new LogAO();
    }

    public SelenideElement waitEndpoint() {
        return endpoint().waitUntil(appears, C.ENDPOINT_INITIALIZATION_TIMEOUT);
    }

    public ToolPageAO clickEndpoint() {
        String endpointURL = waitEndpoint().attr("href");
        sleep(3, MINUTES);
        open(endpointURL);

        return new ToolPageAO(endpointURL);
    }

    public RunsMenuAO validateStatus(final String runId, final LogAO.Status status) {
        $(byClassName("run-" + runId)).find(byCssSelector("i")).shouldHave(cssClass(status.reached.toString()));
        return this;
    }

    public RunsMenuAO validateColumnName(final String... heads) {
        List<String> columns = context().find(byClassName("ant-table-thead")).$$("th")
                .stream()
                .map(SelenideElement::text)
                .collect(toList());
        Arrays.stream(heads).forEach(head -> assertTrue(columns.contains(head),
                format("Column head %s isn't found",head)));
        return this;
    }

    public RunsMenuAO validateAllRunsHaveButton(String button) {
        allRuns().forEach(row -> row.$(byText(button)).shouldBe(visible));
        return this;
    }

    public RunsMenuAO validateRunsHaveButton(final List<String> runIds, final String button) {
        runIds.forEach(id -> $("tbody")
                .find(withText(id))
                .closest(".ant-table-row")
                .find(byText(button))
                .shouldBe(visible)
        );
        return this;
    }

    public RunsMenuAO validateAllRunsHaveCost() {
        allRuns().forEach(row -> row.$(byClassName("ob-estimated-price-info__info"))
                .shouldBe(visible)
                .shouldHave(text("Cost:")));
        return this;
    }

    public RunsMenuAO validateRowsCount(CollectionCondition condition) {
        allRuns().shouldHave(condition);
        return this;
    }

    public ElementsCollection allRuns() {
        return $("tbody").shouldBe(visible).findAll("tr");
    }

    public RunsMenuAO ensureHasOwner(String owner) {
        $$(className("ant-row"))
                .findBy(text("Owner"))
                .parent()
                .shouldHave(text(owner));
        return this;
    }

    public RunsMenuAO assertLatestPipelineHasName(String pipelineName) {
        $("tbody")
                .find("tr")
                .find(byClassName("un-table-columns__run-row-docker-image"))
                .shouldHave(text(pipelineName));
        return this;
    }

    public RunsMenuAO assertLatestPipelineHasRunID(String runId) {
        $("tbody")
                .find("tr")
                .find(byClassName(HeaderColumn.RUN.cssClass))
                .shouldHave(text(runId));
        return this;
    }

    public RunsMenuAO validateOnlyMyPipelines() {
        $(byClassName("ant-table-tbody"))
                .should(exist)
                .findAll(byClassName("un-table-columns__run-row-owner"))
                .excludeWith(text(C.ANOTHER_LOGIN))
                .shouldBe(empty);
        return this;
    }

    public RunsMenuAO validatePipelineOwner(String id, String owner) {
        $(byClassName(format("run-%s", id))).shouldHave(matchText(owner));
        return this;
    }

    public RunsMenuAO validateOnlyUsersPipelines(String username) {
        $(byClassName("ant-table-tbody"))
                .should(exist)
                .findAll(byClassName("un-table-columns__run-row-owner"))
                .excludeWith(text(username))
                .shouldHave(size(1));
        return this;
    }

    public RunsMenuAO validateNoRunsArePresent() {
        $(byClassName("ant-table-placeholder"))
                .shouldBe(visible);
        return this;
    }

    public RunsMenuAO validatePipelineIsPresent(String pipelineName) {
        $(tagName("tbody"))
                .findAll(tagName("tr"))
                .findBy(text(pipelineName)).shouldBe(visible);
        return this;
    }

    public RunsMenuAO validateRunsListIsNotEmpty() {
        $(tagName("tbody"))
                .should(appear)
                .findAll(tagName("tr"))
                .shouldHave(sizeGreaterThan(0));
        return this;
    }

    private void tableShouldAppear() {
        $(byClassName("ant-table-tbody")).should(Condition.or("table appears", appear, tableIsEmpty));
    }

    public SelenideElement endpoint() {
        return $(withText("Endpoint"))
                .closest("tr")
                .find("a");
    }

    public RunsMenuAO stopRunIfPresent(String id) {
        activeRuns().viewAvailableActiveRuns();
        sleep(10, SECONDS);
        if (isActiveRun(id)) {
            stopRun(id);
            System.out.printf("Run with id %s has been stopped.%n", id);
        }
        return this;
    }

    public RunsMenuAO openClusterRuns(String parentRunId) {
        $(byClassName("run-" + parentRunId))
                .find(byClassName("ant-table-row-expand-icon")).shouldBe(visible).click();
        return this;
    }

    public RunsMenuAO shouldContainRunsWithParentRun(int pipelinesNumber, String runId) {
        $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(text(runId))
                .shouldHave(sizeGreaterThanOrEqual(pipelinesNumber));
        return this;
    }

    public RunsMenuAO shouldContainRun(String pipelineName, String runId) {
        return validatePipelineIsPresent(format("%s-%s", pipelineName, runId));
    }

    public RunsMenuAO viewAvailableActiveRuns() {
        $(withText("Currently viewing")).waitUntil(visible, C.DEFAULT_TIMEOUT);
        if ($(elementWithText(tagName("b"), "other available ")).isDisplayed()) {
            $(withText("Currently viewing")).click();
            $(elementWithText(tagName("b"), "other available ")).shouldBe(visible).click();
            sleep(2, SECONDS);
        }
        return this;
    }

    public RunsMenuAO waitUntilPauseButtonAppear(final String runId) {
        $("#run-" + runId + "-pause-button").waitUntil(appear, APPEARING_TIMEOUT);
        return this;
    }

    public RunsMenuAO pause(final String runId, final String pipelineName) {
        $("#run-" + runId + "-pause-button").shouldBe(visible).click();
        $(byClassName("ant-modal-body")).shouldBe(visible);
        ensure(byClassName("ause-confirmation__title"),
               matchText(format("Do you want to pause%s", pipelineName)))
            .sleep(1, SECONDS)
            .click(button("PAUSE"));
        return this;
    }

    public RunsMenuAO terminateRun(final String runId, final String pipelineName) {
        $("#run-" + runId + "-terminate-button").shouldBe(visible).click();
        context().$(byText("Terminate")).waitUntil(visible, DEFAULT_TIMEOUT);
        ensure(byText("Terminate"), text(format("Terminate %s?", pipelineName)))
                .sleep(1, SECONDS)
                .click(button("TERMINATE"));
        $(byText("Terminate")).waitWhile(visible, DEFAULT_TIMEOUT);
        return this;
    }

    public RunsMenuAO ensurePauseButtonDisabled(String runID) {
        context().find(byId(format("run-%s-pause-button", runID)))
                .shouldBe(Conditions.disabled);
        return this;
    }

    public RunsMenuAO checkPauseButtonTooltip(String runID, String message) {
        hover(byId(format("run-%s-pause-button", runID)));
        $(PipelineSelectors.visible(byClassName("ant-popover-inner-content")))
                .shouldHave(text(message));
        return this;
    }

    public RunsMenuAO waitUntilResumeButtonAppear(final String runId) {
        $("#run-" + runId + "-resume-button").waitUntil(appear, COMPLETION_TIMEOUT);
        return this;
    }

    public RunsMenuAO resume(final String runId, final String pipelineName) {
        $("#run-" + runId + "-resume-button").shouldBe(visible).click();
        new ConfirmationPopupAO<>(this)
                .ensureTitleContains(format("Do you want to resume %s", pipelineName))
                .sleep(1, SECONDS)
                .click(button("RESUME"));
        return this;
    }

    public RunsMenuAO waitUntilStopButtonAppear(final String runId) {
        $("#run-" + runId + "-stop-button").waitUntil(appear, APPEARING_TIMEOUT);
        return this;
    }

    public RunsMenuAO waitForCompletion(final String runId) {
        $(byClassName("run-" + runId)).find(byCssSelector("i")).waitUntil(hidden, COMPLETION_TIMEOUT);
        return this;
    }

    public RunsMenuAO waitForInitializeNode(final String runId) {
        final String initializeNodeTaskPath = "//*[contains(@class, 'ant-menu-item') and " +
                ".//*[contains(., 'InitializeNode')]]//*[contains(@class, 'anticon')]";
        int attempts = 15;

        $(taskWithName("InitializeNode")).waitUntil(visible, C.ENDPOINT_INITIALIZATION_TIMEOUT).click();
        while (!$(byXpath(initializeNodeTaskPath)).has(cssClass("cp-runs-table-icon-green"))) {
            if (new LogAO().logMessages().filter(l -> l.contains("Started initialization of new calculation node"))
                    .count() > 2 || attempts == 0) {
                screenshot("failed_node_for_run_" + runId);
                throw new IllegalArgumentException(format("Node for %s run was not initialized", runId));
            }
            sleep(1, MINUTES);
            attempts--;
        }
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }

    public RunsMenuAO filterBy(HeaderColumn header, String ip) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(header.cssClass));
        createdHeaderButton.find(byAttribute("title", "Filter menu")).click();
        switch (header) {
            case PIPELINE:
                inputFilterValue(ip);
                $(byXpath(format(".//span[.='%s']/preceding-sibling::span[@class='ant-checkbox']", ip)))
                        .click();
                break;
            case DOCKER_IMAGE:
                inputFilterValue(ip);
                $(byXpath(format(".//span[.='%s']", ip))).parent()
                        .$(byXpath("preceding-sibling::span[@class='ant-checkbox']")).click();
                break;
            default:
                throw new IllegalArgumentException("Could be filtered only by Label of Address");
        }
        $$(byText("OK")).find(visible).click();
        return this;
    }

    public RunsMenuAO resetFiltering(HeaderColumn header) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(header.cssClass));
        createdHeaderButton.find(byAttribute("title", "Filter menu")).click();

        $$(byText("Clear")).find(visible).click();
        return this;
    }

    private void inputFilterValue(String value) {
        $(byClassName("un-table-columns__filter-popover-container"))
                .$$("input").findBy(attribute("placeholder", "Filter"))
                .setValue(value);
    }

    public boolean isActiveRun(final String id) {
        return $(tagName("tbody")).shouldBe(visible)
                .findAll(tagName("tr")).findBy(text(id)).is(exist);
    }

    public String getRunIdByTag(final String runTag) {
         return $(tagName("tbody"))
                .shouldBe(visible)
                .findAll(className("ant-table-row"))
                .stream()
                .filter(element -> element.find(byClassName("un-tags__more-label")).exists())
                .filter(element -> {
                        element.find(byClassName("un-tags__more-label")).hover();
                        $(byClassName("un-tags__run-tag")).has(text(runTag.toUpperCase()));
                        return true;
                    }
                )
                .findFirst()
                .orElseThrow(() -> new NoSuchWindowException(format("No such run with tag {%s}.", runTag)))
                .find(byClassName("un-name__original"))
                .getText().replace("pipeline-","");
    }

    public enum HeaderColumn {
        RUN("un-table-columns__run-row-name"),
        PARENT_RUN("un-table-columns__run-row-parent-run"),
        PIPELINE("un-table-columns__run-row-pipeline"),
        DOCKER_IMAGE("un-table-columns__run-row-docker-image"),
        STARTED("un-table-columns__run-row-started"),
        COMPLETED("un-table-columns__run-row-completed"),
        ELAPSED("un-table-columns__run-row-elapsed-time"),
        OWNER("un-table-columns__run-row-owner");

        private String cssClass;

        HeaderColumn(String cssClass) {
            this.cssClass = cssClass;
        }
    }
}
