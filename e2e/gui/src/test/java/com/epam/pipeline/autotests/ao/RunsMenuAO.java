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

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Driver;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.ElementNotFound;
import com.codeborne.selenide.impl.Alias;
import com.epam.pipeline.autotests.utils.C;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.CollectionCondition.empty;
import static com.codeborne.selenide.CollectionCondition.*;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.driver;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.utils.C.COMPLETION_TIMEOUT;
import static com.epam.pipeline.autotests.utils.C.ENDPOINT_INITIALIZATION_TIMEOUT;
import static com.epam.pipeline.autotests.utils.C.SSH_APPEARING_TIMEOUT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.elementWithText;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;
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
        public boolean apply(Driver driver, final WebElement ignored) {
            return $(byClassName("ant-table-placeholder")).is(visible);
        }
    };

    public static By runOf(final String pipelineName) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $("tbody")
                        .findAll("tr").stream()
                        .filter(element -> element.has(text(pipelineName)))
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
        runStopButton.shouldBe(enabled, ofMillis(DEFAULT_TIMEOUT)).click();
        sleep(3, SECONDS);
        if (!$(button("STOP")).isEnabled()) {
            runStopButton.shouldBe(enabled, ofMillis(DEFAULT_TIMEOUT)).click();
        }
        $(button("STOP")).click();
        return this;
    }

    public RunsMenuAO show(String runId) {
        $("#run-" + runId + "-logs-button").should(exist).click();
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
                throw new ElementNotFound(driver(), new Alias($(byClassName("log__run-title")).getAlias()),
                        format("Could not get run logs (screenshot: %s.png)", GET_LOGS_ERROR), exist);
            }

            show(runId);
            sleep(2, SECONDS);
        }

        return new LogAO();
    }

    public SelenideElement waitEndpoint() {
        return endpoint().shouldBe(appears, ofMillis(C.ENDPOINT_INITIALIZATION_TIMEOUT));
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
        return $("tbody").shouldBe(visible).findAll("tr").filter(visible);
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
        activeRuns();
        sleep(10, SECONDS);
        if ($(tagName("tbody")).shouldBe(exist).findAll(tagName("tr")).findBy(text(id)).is(exist)) {
            stopRun(id);
            System.out.printf("Run with id %s has been stopped.%n", id);
        }
        return this;
    }

    public RunsMenuAO openClusterRuns(String parentRunId) {
        sleep(5, SECONDS);
        if($(byClassName("run-" + parentRunId))
                .find(byClassName("ant-table-row-expand-icon"))
                .shouldBe(visible)
                .has(cssClass("ant-table-row-collapsed"))) {
            $(byClassName("run-" + parentRunId))
                    .find(byClassName("ant-table-row-expand-icon"))
                    .shouldBe(visible).click();
            sleep(5, SECONDS);
        }
        return this;
    }

    public RunsMenuAO shouldContainRunsWithParentRun(int pipelinesNumber, String runId) {
        $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(text(runId))
                .shouldHave(sizeGreaterThanOrEqual(pipelinesNumber));
        return this;
    }

    public RunsMenuAO shouldContainExactlyRunsWithParentRun(final int pipelinesNumber, final String parentRunId) {
        $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(exactText(parentRunId))
                .shouldHave(size(pipelinesNumber));
        return this;
    }

    public RunsMenuAO childRunsShouldHaveStatus(final String parentRunId, final LogAO.Status status) {
        $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(exactText(parentRunId))
                .shouldHave(sizeGreaterThan(0))
                .forEach(parentRunCell -> parentRunCell.closest("tr").shouldHave(status.reached));
        return this;
    }

    public RunsMenuAO runShouldHaveParentRun(final String runId, final String parentRunId) {
        $(byClassName("run-" + runId))
                .shouldBe(visible)
                .find(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .shouldHave(exactText(parentRunId));
        return this;
    }

    /**
     * Returns the ids of the child runs of the given parent run. Only the rows on the screen count,
     * so the parent run has to be expanded first.
     */
    public List<String> childRunIds(final String parentRunId) {
        final Pattern runRowClass = Pattern.compile("\\brun-(\\d+)\\b");
        return $$(byCssSelector("td.un-table-columns__run-row-parent-run"))
                .filter(exactText(parentRunId))
                .shouldHave(sizeGreaterThan(0))
                .stream()
                .map(parentRunCell -> runRowClass.matcher(parentRunCell.closest("tr").getAttribute("class")))
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .collect(toList());
    }

    public RunsMenuAO runCellShouldHave(final String runId, final HeaderColumn column, final Condition... conditions) {
        $(byClassName("run-" + runId))
                .shouldBe(visible)
                .find(byClassName(column.cssClass))
                .shouldHave(conditions);
        return this;
    }

    /**
     * Checks that the status icon of the run is one of a run that still waits for its node or its docker image:
     * scheduled, queued or pulling.
     */
    public RunsMenuAO runShouldBeWaiting(final String runId) {
        $(byClassName("run-" + runId))
                .shouldBe(visible)
                .find(byCssSelector("i"))
                .shouldHave(Condition.or("waiting status icon",
                        cssClass("anticon-loading"), cssClass("anticon-hourglass"), cssClass("anticon-download")));
        return this;
    }

    /**
     * Waits until the status icon of the run is the one of a running run. The icon color does not tell it:
     * scheduled, queued, pulling, running and paused runs have the same color.
     */
    public RunsMenuAO runShouldBeRunning(final String runId) {
        $(byClassName("run-" + runId))
                .shouldBe(visible)
                .find(byCssSelector("i"))
                .shouldHave(cssClass("anticon-play-circle-o"), ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public RunsMenuAO pausingLabelShouldAppear(final String runId) {
        $("#run-" + runId + "-pausing").shouldBe(visible, ofMillis(DEFAULT_TIMEOUT)).shouldHave(exactText("PAUSING"));
        return this;
    }

    public RunsMenuAO resumingLabelShouldAppear(final String runId) {
        $("#run-" + runId + "-resuming").shouldBe(visible, ofMillis(DEFAULT_TIMEOUT)).shouldHave(exactText("RESUMING"));
        return this;
    }

    /**
     * Checks that the row of the run is shown and has neither a STOP nor a TERMINATE action.
     */
    public RunsMenuAO stopAndTerminateShouldBeUnavailable(final String runId) {
        $(byClassName("run-" + runId)).shouldBe(visible);
        $("#run-" + runId + "-stop-button").shouldNot(exist);
        $("#run-" + runId + "-terminate-button").shouldNot(exist);
        return this;
    }

    public RunsMenuAO shouldContainRun(String pipelineName, String runId) {
        return validatePipelineIsPresent(format("%s-%s", pipelineName, runId));
    }

    public RunsMenuAO viewAvailableActiveRuns() {
        $(withText("Currently viewing")).shouldBe(visible, ofMillis(C.DEFAULT_TIMEOUT));
        if ($(elementWithText(tagName("a"), "View other available active runs")).isDisplayed()) {
            $(elementWithText(tagName("a"), "View other available active runs")).shouldBe(visible).click();
            sleep(2, SECONDS);
        }
        $(withText("Currently viewing")).hover();
        return this;
    }

    public RunsMenuAO waitUntilPauseButtonAppear(final String runId) {
        $("#run-" + runId + "-pause-button").shouldBe(appear, ofMillis(APPEARING_TIMEOUT));
        return this;
    }

    /**
     * The pause and resume confirmations are modals of their own, not ant-confirm ones. The title is the question
     * and the run name with a CSS margin, not a space, between them: a pipeline name with its version, or the image
     * name of a tool without its group and tag (client/src/components/runs/actions/pause-confirmation.js,
     * resume-confirmation.js, run-display-name.js). The confirm button stays disabled while the dialog loads its data:
     * the layers and commit checks of the run to pause, the instance types of the run to resume.
     */
    public RunsMenuAO pause(final String runId, final String pipelineName) {
        $("#run-" + runId + "-pause-button").shouldBe(visible).click();
        $(byClassName("ause-confirmation__body")).shouldBe(visible);
        ensure(byClassName("ause-confirmation__title"), confirmationTitle("Do you want to pause", pipelineName))
                .sleep(1, SECONDS)
                .click(button("PAUSE"));
        return this;
    }

    private static Condition confirmationTitle(final String question, final String runName) {
        return matchText(format("%s\\s*%s", Pattern.quote(question), Pattern.quote(runName)));
    }

    public RunsMenuAO waitUntilResumeButtonAppear(final String runId) {
        $("#run-" + runId + "-resume-button").shouldBe(appear, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public RunsMenuAO resume(final String runId, final String pipelineName) {
        $("#run-" + runId + "-resume-button").shouldBe(visible).click();
        $(byClassName("esume-confirmation__body")).shouldBe(visible);
        ensure(byClassName("esume-confirmation__title"), confirmationTitle("Do you want to resume", pipelineName))
                .sleep(1, SECONDS)
                .click(button("RESUME"));
        return this;
    }

    public RunsMenuAO waitUntilStopButtonAppear(final String runId) {
        $("#run-" + runId + "-stop-button").shouldBe(appear, ofMillis(APPEARING_TIMEOUT));
        return this;
    }

    public RunsMenuAO waitForCompletion(final String runId) {
        $(byClassName("run-" + runId)).find(byCssSelector("i")).shouldBe(hidden, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public RunsMenuAO waitForInitializeNode(final String runId) {
        final String initializeNodeTaskPath = ".//*[contains(@class, 'ant-menu-item') and " +
                ".//*[contains(., 'InitializeNode')]]//*[contains(@class, 'anticon')]";
        int attempts = 15;

        $(taskWithName("InitializeNode")).shouldBe(visible, ofMillis(C.ENDPOINT_INITIALIZATION_TIMEOUT)).click();
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
                inputFilterValue("Filter pipelines", ip);
                $(byXpath(format(".//span[.='%s']/preceding-sibling::span[@class='ant-checkbox']", ip)))
                        .click();
                break;
            case DOCKER_IMAGE:
                inputFilterValue("Filter docker images", ip);
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

    /**
     * Every column filter has a popover of its own, and a closed one stays hidden in the page. The pipelines and the
     * docker images filters list nothing until the search has 3 characters
     * (client/src/components/runs/run-table/columns/column-pipeline.js, column-docker.js).
     */
    private void inputFilterValue(final String placeholder, final String value) {
        $$(byClassName("un-table-columns__filter-popover-container")).findBy(visible)
                .$$("input").findBy(attribute("placeholder", placeholder))
                .setValue(value);
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
