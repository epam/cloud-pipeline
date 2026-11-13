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
import com.codeborne.selenide.Driver;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Quotes;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.switchTo;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;
import static org.openqa.selenium.By.className;
import static org.testng.Assert.assertTrue;

public class LogAO implements AccessObject<LogAO> {
    public static final long SSH_LINK_APPEARING_TIMEOUT = C.SSH_APPEARING_TIMEOUT;
    public static final long COMMIT_BUTTON_APPEARING_TIMEOUT = C.COMMIT_APPEARING_TIMEOUT;
    public static final long COMMITTING_TIMEOUT = C.COMMITTING_TIMEOUT;
    public static final long COMPLETION_TIMEOUT = C.COMPLETION_TIMEOUT;
    private static final int FINISHED_RUN_TASK_RELOADS = 6;
    public static final long BUCKETS_MOUNTING_TIMEOUT = C.BUCKETS_MOUNTING_TIMEOUT;
    // "Expand more" adds 500 lines to the log shown: 100 clicks show a log of 50000 lines
    private static final int MAX_LOG_EXPANSIONS = 100;

    private static Condition completed = Condition.or("finished", Status.SUCCESS.reached, Status.STOPPED.reached, Status.FAILURE.reached);
    private static Condition running = Condition.or("running", Status.LOADING.reached, Status.WORKING.reached);

    private final Map<Primitive,SelenideElement> elements = initialiseElements(
            entry(STATUS, $(byClassName("log__run-title"))),
            entry(TITLE, $(byClassName("log__run-title")).find(byClassName("log__pipeline-link"))),
            entry(SSH_LINK, $$(tagName("a")).findBy(exactText("SSH"))),
            entry(COMMIT, $$(tagName("a")).findBy(exactText("COMMIT"))),
            entry(PAUSE, $$(tagName("a")).findBy(exactText("PAUSE"))),
            entry(RESUME, $$(tagName("a")).findBy(exactText("RESUME"))),
            entry(STOP, $$(tagName("a")).findBy(exactText("STOP"))),
            entry(RERUN, $$(tagName("a")).findBy(exactText("RERUN"))),
            entry(ENDPOINT, $(withText("Endpoint")).closest("tr").find("a")),
            entry(INSTANCE, context().find(byXpath(".//*[.//*[text()[contains(.,'Instance')]] and contains(@class, 'ant-collapse')]"))),
            entry(PARAMETERS, context().find(byXpath(".//*[.//*[text()[contains(.,'Parameters')]] and contains(@class, 'ant-collapse')]"))),
            // the first link of the row is "show cluster usage": the nested runs are the log__nested-run links
            // (client/src/components/runs/logs/Log.js:1437-1451, 1471-1497)
            entry(NESTED_RUNS, $(withText("Nested runs:")).closest("tr").find(".log__nested-run")),
            entry(SHARE_WITH, $(withText("Share with:")).closest("tr").find("a"))
    );

    public LogAO waitForCompletion() {
        get(STATUS).shouldBe(completed, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public LogAO waitFor(final Status expectedStatus) {
        get(STATUS).shouldBe(expectedStatus.reached, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public LogAO waitForSshLink() {
        get(SSH_LINK).shouldBe(visible, ofMillis(SSH_LINK_APPEARING_TIMEOUT));
        return this;
    }

    public ShellAO clickOnSshLink() {
        return ShellAO.open(getSshLink());
    }

    // The SSH terminal opens in the same browser tab: if the shell actions fail, the terminal is left before the error
    // is rethrown, so that the next tests of the class start in the platform UI (they failed with "element not found")
    public LogAO ssh(final Consumer<ShellAO> shell) {
        final ShellAO shellAO = clickOnSshLink();
        try {
            shell.accept(shellAO);
        } catch (RuntimeException | Error e) {
            try {
                shellAO.close();
            } catch (RuntimeException | Error closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
        return this;
    }

    /**
     * Clicks the SSH link, which opens the terminal in a new browser tab, and switches into that tab.
     * Fails if no new tab is opened.
     */
    public ShellAO clickSshLinkInNewTab() {
        get(SSH_LINK).shouldBe(visible).click();
        switchTo().window(1);
        switchTo().frame(0);
        return new ShellAO();
    }

    /**
     * Performs the action in the terminal opened by a click on the SSH link, then closes that tab
     * and returns to the run page.
     */
    public LogAO sshInNewTab(final Consumer<ShellAO> shell) {
        final ShellAO terminal = clickSshLinkInNewTab();
        try {
            shell.accept(terminal);
        } finally {
            terminal.closeTab();
        }
        return this;
    }

    public LogAO waitForCommitButton() {
        get(COMMIT).shouldBe(visible, ofMillis(COMMIT_BUTTON_APPEARING_TIMEOUT));
        return this;
    }

    public LogAO shouldHaveStatus(Status status) {
        get(STATUS).shouldHave(status.reached, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public LogAO shouldHaveRunningStatus() {
        get(STATUS).shouldHave(running);
        return this;
    }

    /**
     * All the lines of the log of the selected task. The task's log is shown in a console that is not a virtualized
     * list: it renders every line it shows, each with its number and date, but only the last 500 lines of a long log
     * (client/src/components/runs/run-task-logs/index.js:30,145-175,632-780). So the earlier lines are added with
     * "Expand more" until the whole log is shown, and then all the lines are read.
     */
    public Stream<String> logMessages() {
        final SelenideElement console = $(log()).shouldBe(visible);
        // a spinner is shown instead of the lines while the log of the task is loaded
        console.find(byClassName("un-task-logs__pending-container")).shouldNot(exist);
        for (int expansion = 0; expansion < MAX_LOG_EXPANSIONS; expansion++) {
            if (!expandLog()) {
                break;
            }
        }
        // a line is its number, its date and its message, in spans with no space between them: the message only
        return console.findAll(byCssSelector(".un-task-logs__console-line > span:last-child")).texts().stream();
    }

    /**
     * Clicks "Expand more" of the log if the log shown is the last part of a long log: it adds 500 earlier lines
     * (client/src/components/runs/run-task-logs/index.js:393-414,643-659).
     *
     * @return false if the whole log is shown already
     */
    private static boolean expandLog() {
        final SelenideElement expandMore = $(log()).find(byXpath(
                ".//*[contains(@class, 'un-task-logs__expand-more')]//a[normalize-space(.) = 'Expand more']"));
        if (!expandMore.is(visible)) {
            return false;
        }
        expandMore.click();
        return true;
    }

    public String getSshLink() {
        return get(SSH_LINK).shouldBe(visible).attr("href");
    }

    public LogAO commit(final Consumer<CommitPopup> commit) {
        commit.accept(openCommitDialog());
        return this;
    }

    private CommitPopup openCommitDialog() {
        get(COMMIT).shouldBe(visible).click();
        return new CommitPopup(this);
    }

    public LogAO assertCommittingFinishedSuccessfully() {
        return messageShouldAppear("COMMITTING", 600000)
                .messageShouldAppear("COMMIT SUCCEEDED", COMMITTING_TIMEOUT);
    }

    public LogAO waitForPauseButton() {
        get(PAUSE).shouldBe(visible, ofMillis(SSH_LINK_APPEARING_TIMEOUT));
        return this;
    }

    public LogAO clickOnPauseButton() {
        get(PAUSE).shouldBe(visible).click();
        return this;
    }

    /**
     * Pauses the run in its own dialog, not in a confirmation popup: its title is "Do you want to pause" and the name
     * of the run, a pipeline as {@code <b>pipeline</b> (version)}, a tool as its image name, then "?"
     * (client/src/components/runs/actions/pause-confirmation.js:158-166,215-228, run-display-name.js).
     */
    public LogAO pause(final String pipelineName) {
        clickOnPauseButton();
        ensure(byClassName("ause-confirmation__title"), visible,
                matchText(runConfirmationTitle("pause", pipelineName)))
                .sleep(1, SECONDS)
                .click(button(PAUSE.name()));
        return this;
    }

    /**
     * The title of the pause and resume dialogs of a run: the gap before the name of the run is a CSS margin, not a
     * space, so the text has none. The version of a pipeline follows its name.
     */
    private static String runConfirmationTitle(final String action, final String runName) {
        return format("Do you want to %s\\s*%s( \\(.+\\))?\\?", action, Pattern.quote(runName));
    }

    public LogAO clickOnStopButton() {
        get(STOP).shouldBe(visible).click();
        return this;
    }

    public LogAO stop(final String pipelineName) {
        clickOnStopButton();
        new ConfirmationPopupAO<>(this)
                .ensureTitleIs(
                        format("Stop %s?", pipelineName))
                .sleep(1, SECONDS)
                .click(button(STOP.name()));
        return this;
    }

    public PipelineRunFormAO clickOnRerunButton() {
        get(RERUN).shouldBe(visible).click();
        return new PipelineRunFormAO();
    }

    public LogAO assertPausingFinishedSuccessfully() {
        return assertPausingStatus().waitForResumeButton();
    }

    public LogAO assertPausingStatus() {
        return messageShouldAppear("PAUSING");
    }

    public LogAO waitForResumeButton() {
        get(RESUME).shouldBe(visible, ofMillis(SSH_LINK_APPEARING_TIMEOUT));
        return this;
    }

    /**
     * Resumes the run in its own dialog, like {@link #pause(String)}: "Do you want to resume" and the name of the run
     * (client/src/components/runs/actions/resume-confirmation.js:243-251,321-333). Its RESUME button is enabled once
     * the node types of the dialog are loaded.
     */
    public LogAO resume(final String pipelineName) {
        get(RESUME).shouldBe(visible).click();
        ensure(byClassName("esume-confirmation__title"), visible,
                matchText(runConfirmationTitle("resume", pipelineName)))
                .sleep(2, SECONDS)
                .click(button(RESUME.name()));
        return this;
    }

    public LogAO assertResumingFinishedSuccessfully() {
        return messageShouldAppear("RESUMING").waitForPauseButton();
    }

    public LogAO waitForEndpointLink() {
        get(ENDPOINT).shouldBe(appears, ofMillis(SSH_LINK_APPEARING_TIMEOUT));
        return this;
    }

    public ToolPageAO clickOnEndpointLink() {
        String endpoint = getEndpointLink();
        get(ENDPOINT).click();
        switchTo().window(1);
        return new ToolPageAO(endpoint);
    }

    public ToolPageAO clickOnEndpointLink(String link) {
        String endpoint = getEndpointLink(link);
        $(byXpath(format(".//a[.='%s']", link))).click();
        switchTo().window(1);
        return new ToolPageAO(endpoint);
    }

    public String getEndpointLink() {
        return get(ENDPOINT).shouldBe(visible).attr("href");
    }

    public String getEndpointName() {
        return get(ENDPOINT).shouldBe(visible).text();
    }

    public String getEndpointLink(String link){
        return $(withText("Endpoint")).closest("tr").$(byXpath(format(".//a[.='%s']", link)))
                .shouldBe(visible).attr("href");
    }

    public LogAO checkEndpointLink(String endpoint, String link) {
        return ensure(withText(endpoint), attribute("href", link));
    }

    public LogAO waitForNestedRunsLink() {
        long startTime = currentTimeMillis();
        while(!get(NESTED_RUNS).exists() &&
                (currentTimeMillis()-startTime) < SSH_LINK_APPEARING_TIMEOUT) {
            ensure(STATUS, not(completed));
            sleep(20, SECONDS);
        }
        return this;
    }

    public LogAO clickOnNestedRunLink() {
        get(NESTED_RUNS).click();
        return this;
    }

    public String getNestedRunID(int childNum) {
        // the nested runs are links in a block of their own, under the "N nested runs - show cluster usage" line
        // (client/src/components/runs/logs/Log.js:1471-1497)
        return $(withText("Nested runs:")).closest("tr").find(byXpath(format(
                "td/div[contains(@class, 'log__nested-runs')]/a[contains(@class, 'log__nested-run')][%s]/b", childNum)))
                .getText();
    }

    public LogAO waitForNestedRunWorking(String childRunID) {
        $(byAttribute("href", format("#/run/%s", childRunID)))
                .$(byXpath(".//i[contains(@class, 'anticon')]"))
                .shouldBe(cssClass("anticon-play-circle-o"), ofMillis(COMPLETION_TIMEOUT));
        return this;
    } 

    public LogAO shareWithGroup(final String groupName) {
        click(SHARE_WITH);
        new ShareWith().addGroupToShare(groupName);
        return this;
    }

    public LogAO shareWithUser(final String userName, boolean sshConnection) {
        click(SHARE_WITH);
        new ShareWith().addUserToShare(userName, sshConnection);
        return this;
    }

    public LogAO setEnableSShConnection(final String name) {
        click(SHARE_WITH);
        new ShareWith().selectEnableSShConnection(name);
        return this;
    }

    public LogAO removeShareUserGroup(final String name) {
        click(SHARE_WITH);
        new ShareWith().removeUserFromShare(name);
        return this;
    }

    public LogAO validateShareLink(final String link) {
        get(SHARE_WITH).shouldHave(text(link));
        return this;
    }

    public LogAO validateException(final String exception) {
        $(byClassName("ant-alert-error")).shouldHave(text(exception));
        return this;
    }

    public LogAO waitForLog(final String message) {
        for (int i = 0; i < 70; i++) {
            refresh();
            sleep(10, SECONDS);
            if ($(log()).shouldBe(visible).is(matchText(message))) {
                break;
            }
            sleep(10, SECONDS);
        }
        return this;
    }

    public LogAO waitForTask(final String task) {
        $(taskWithName(task)).shouldBe(visible, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    /**
     * Waits for a task of a run that has already finished. The run's final status can come before the logs of its
     * last task: the status of a run can be final a few seconds before the log of its pipeline task arrives, and the
     * page of a finished run then did not show the task 30 s later (EPMCMBIBPC-396).
     * So the page is reloaded, as a user reloads it, every 10 s until the task is shown, at most 6 times.
     */
    public LogAO waitForTaskOfFinishedRun(final String task) {
        for (int reload = 0; reload < FINISHED_RUN_TASK_RELOADS && !$(taskWithName(task)).is(visible); reload++) {
            sleep(10, SECONDS);
            refresh();
            get(STATUS).shouldBe(visible);
        }
        $(taskWithName(task)).shouldBe(visible);
        return this;
    }

    public LogAO waitForTaskStatus(final String task, Status status) {
        $(taskWithName(task)).shouldBe(status.reached, ofMillis(COMPLETION_TIMEOUT));
        return this;
    }

    public LogAO instanceParameters(final Consumer<InstanceParameters> action) {
        expandTab(INSTANCE);
        action.accept(new InstanceParameters());
        return this;
    }

    public LogAO clickTaskWithName(final String name) {
        return click(taskWithName(name))
               .ensure(byXpath(".//div[contains(@class,'cp-console-output')]/div[contains(@class,'un-task-logs__console-line')]"), exist);
    }

    /**
     * Shows the timings of the tasks. The "SHOW TIMINGS" link is a clock icon above the task list that switches them
     * on and off, and the task list has the class log__task-list-timings while they are shown
     * (client/src/components/runs/logs/Log.js:1132-1139,1159-1164): so it is clicked only if they are hidden.
     */
    public LogAO showTimings() {
        if (!$(byClassName("log__task-list-timings")).exists()) {
            $(byClassName("log__timing-btn")).find(tagName("a")).shouldBe(visible).click();
        }
        $(byClassName("log__task-list-timings")).shouldBe(visible);
        return this;
    }

    public LogAO clickMountBuckets() {
        waitForMountBuckets().closest("a").click();
        return this;
    }


    public String getParameterValue(final String name) {
        expandTab(INSTANCE);
        return $(InstanceParameters.parameterWithName(name)).text();
    }

    /**
     * @deprecated Use {@link #configurationParameter(String, String)} instead
     * @see #configurationParameter(String, String)
     */
    public LogAO ensureParameterIsPresent(String name, String value) {
        $(parameterWithName(name, value)).shouldBe(visible);
        return this;
    }

    public LogAO ensureOnOfManyParametersIsPresent(String name, List<String> values) {
        for (String value : values) {
            if ($(parameterWithName(name, value)).is(visible)) {
                return this;
            }
        }
        screenshot("ParametersNoPresent");
        throw new AssertionError("Valid parameter value is absent.");
    }

    /**
     * Checks that the run has no parameter with this name. The page shows the Parameters panel, when the run has
     * parameters, with the other panels of the run info, so this waits for the Instance panel first. A collapsed panel
     * that was never expanded renders no rows (rc-collapse), so the Parameters panel is expanded before the check.
     */
    public LogAO ensureParameterIsNotPresent(String name) {
        get(INSTANCE).shouldBe(visible);
        if (get(PARAMETERS).exists()) {
            expandTab(PARAMETERS);
        }
        $(byXpath(format(
                ".//tr[.//td[contains(@class, 'log__task-parameter-name') " +
                        "and contains(.//text(), '%s')]]", name))).shouldNot(exist);
        return this;
    }

    /**
     * Selects entry of a parameters panel on a run page.
     *
     * Pay attention there is no need to add any colon, use clean {@code name} and {@code value}.
     * @return Qualifier of a parameter with such {@code name} and {@code value}.
     */
    public static By configurationParameter(final String name, final String value) {
        return byXpath(format(".//*/*[normalize-space(translate(., ' \t\n', '   ')) = '%s:%s']", name, value));
    }

    public LogAO validateRunTitle(String title) {
        return ensure(TITLE, text(title));
    }

    public SelenideElement waitForMountBuckets() {
        return $(byXpath(".//*[contains(@class, 'ant-menu-item') and .//*[contains(., 'MountDataStorages')]]//*[contains(@class, 'anticon')]"))
                .shouldBe(cssClass("cp-runs-table-icon-green"), ofMillis(BUCKETS_MOUNTING_TIMEOUT));
    }

    /**
     * The run id of the title: "Run" and "#&lt;id&gt;" are separate elements, the gap between them is a CSS
     * pseudo-element, so this element has the text "#&lt;id&gt;" (client/src/components/runs/logs/Log.js:1816-1832,
     * client/src/components/runs/run-name/run-name.js:172-185, run-name.css:22-26).
     */
    public static By runId() {
        return byXpath(".//h1[contains(@class, 'log__run-title')]//*[contains(concat(' ', @class, ' '), ' cp-run-name ')]");
    }

    public static By pipelineLink() {
        return byXpath(".//h1[contains(@class, 'log__run-title')]//*[@class = 'log__pipeline-link']");
    }

    public static By detailsWithLabel(final String label) {
        Objects.requireNonNull(label);
        return byXpath(format(".//tr[.//th[normalize-space(text()) = '%s:']]//td", label));
    }

    public static By taskList() {
        return byXpath(".//ul[contains(@class, 'log__task-list')]");
    }

    public static By task() {
        return Combiners.confine(tagName("li"), taskList(), "task");
    }

    public static By taskWithName(final String name) {
        Objects.requireNonNull(name);
        final By taskQualifier = byXpath(format(".//li[contains(., '%s')]", name));
        return Combiners.confine(taskQualifier, taskList(), format("task with name %s", name));
    }

    public static By parameterWithName(final String name, final String value) {
        Objects.requireNonNull(name);
        return byXpath(format(
                ".//tr[.//td[contains(@class, 'log__task-parameter-name') and contains(.//text(), '%s')] and " +
                        ".//td[contains(., '%s')]]", name, value));
    }

    public LogAO checkMountLimitsParameter(String...storages) {
        $(byText("CP_CAP_LIMIT_MOUNTS")).$(By.xpath("following::td"))
                .findAll(tagName("a"))
                .shouldHave(CollectionCondition.size(storages.length));
        Arrays.stream(storages)
                .forEach(storage -> $(byText("CP_CAP_LIMIT_MOUNTS")).$(By.xpath("following::td"))
                        .shouldHave(text(storage)));
        return this;
    }

    public StorageContentAO openStorageFromLimitMountsParameter(String storage) {
        $(byText("CP_CAP_LIMIT_MOUNTS")).$(By.xpath("following::td"))
                .shouldHave(text(storage)).click();
        return new StorageContentAO();
    }

    /**
     * The console with the log of the selected task (client/src/components/runs/run-task-logs/index.js:632-640).
     */
    public static By log() {
        return byClassName("cp-console-output");
    }

    public LogAO logContainsMessage(Set<String> logMess, final String message) {
        assertTrue(logMess.stream().anyMatch(mes -> mes.contains(message)), format("Message '%s' isn't contained in log", message));
        return this;
    }

    public LogAO logNotContainsMessage(Set<String> logMess, final String message) {
        assertTrue(logMess.stream().noneMatch(mes -> mes.contains(message)), format("Message '%s' is contained in log", message));
        return this;
    }

    public LogAO checkAvailableStoragesCount(Set<String> logMess, int count) {
        String str = logMess.stream().filter(Pattern.compile("\\d+ available storage\\(s\\)\\. Checking mount options\\.")
                        .asPredicate()).findFirst().toString();
        Matcher matcher = Pattern.compile(" \\d* ").matcher(str);
        assert matcher.find();
        int res = Integer.parseInt(matcher.group().replace(" ", ""));
        assertTrue(res >= count,
               format("Available storages count (actual %s) should be more or equal %s", res, count));
        return this;
    }

    public static By logMessage(final String text) {
        Objects.requireNonNull(text);
        // a line of the console (client/src/components/runs/run-task-logs/index.js:740-779)
        final String messageClass = "un-task-logs__console-line";
        // Quotes.escape makes an XPath string literal of any text: a message with double quotes (e.g.
        // > export CP_CAP_AUTOSCALE="true") inside a "..." literal would make the selector invalid
        final By messageQualifier = byXpath(format(
                ".//*[contains(concat(' ', @class, ' '), ' %s ') and .//*[contains(., %s)]]",
                messageClass, Quotes.escape(text)
        ));
        return Combiners.confine(messageQualifier, log(), format("log message with text {%s}", text));
    }

    public static By timeInfo(final String label) {
        Objects.requireNonNull(label);
        return byXpath(format(
            ".//*[@class = 'task-link__time-info' and contains(.//text(), '%s')]",
            label
        ));
    }

    public static Condition containsMessage(final String text) {
        Objects.requireNonNull(text);
        return new Condition(format("contains message {%s}", text)) {

            private final By message = logMessage(text);

            @Override
            public boolean apply(Driver driver, final WebElement log) {
                // the message locator is confined to the log already: searched from the page, not inside the log
                // (inside it, it would look for a log in the log and never match)
                boolean seen = $(message).exists();
                // The console is not a virtualized list: it renders every line it shows, also the lines scrolled out
                // of it. But it shows only the last 500 lines of a long log, so the earlier lines are added with
                // "Expand more" until the message is there or the whole log is shown.
                for (int expansion = 0; !seen && expansion < MAX_LOG_EXPANSIONS && expandLog(); expansion++) {
                    seen = $(message).exists();
                }
                return seen;
            }
        };
    }

    public static Condition containsMessages(final String... texts) {
        Objects.requireNonNull(texts);
        return new Condition("contains messages") {

            private final List<String> missingMessages = new ArrayList<>();

            @Override
            public boolean apply(Driver driver, final WebElement logElement) {
                // Selenide calls apply until it is true or the timeout ends: each call checks anew, a message
                // missed by an earlier call (e.g. while the log was refreshed) must not stay missing
                missingMessages.clear();
                Arrays.stream(texts)
                        .map(String::trim)
                        .filter(expectedText -> !containsMessage(expectedText).apply(driver, logElement))
                        .forEach(missingMessages::add);
                return missingMessages.isEmpty();
            }

            public String actualValue(final WebElement logElement) {
                final String allMissingMessages = String.join("\n", missingMessages);
                return format("Following messages wasn't found in log:%n%s", allMissingMessages);
            }
        };
    }

    public static class InstanceParameters implements AccessObject<InstanceParameters> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(TYPE, context().find(parameterWithName("Node type"))),
                entry(DISK, context().find(parameterWithName("Disk"))),
                entry(IMAGE, context().find(parameterWithName("Docker image"))),
                entry(DEFAULT_COMMAND, context().find(parameterWithName("Cmd template"))),
                entry(TIMEOUT, context().find(parameterWithName("Timeout"))),
                entry(PRICE_TYPE, context().find(parameterWithName("Price type"))),
                entry(IP, context().find(parameterWithName("IP"))),
                entry(NODE_IMAGE, context().find(parameterWithName("Node image")))
        );

        public static By parameterWithName(final String name) {
            final String parameterName = "log__node-parameter-name";
            final String parameterValue = "log__node-parameter-value";
            return byXpath(format(
                ".//*[contains(@class, '%s') and text() = '%s']/following-sibling::*[contains(@class, '%s')]",
                parameterName, name, parameterValue
            ));
        }

        public static String getParameterValueLink(final String name) {
            final String parameterValue = $(parameterWithName(name)).text();
            return $$(byCssSelector("a")).find(text(parameterValue)).attr("href");
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public enum Status {
        SUCCESS("cp-runs-table-icon-green"),
        FAILURE("cp-runs-table-icon-red"),
        STOPPED("cp-runs-table-icon-yellow"),
        WORKING("cp-runs-table-icon-blue"),
        LOADING("anticon-loading"),
        PAUSED("anticon-pause-circle-o");

        public final Condition reached;

        Status(String iconClass) {
            this.reached = new Condition("status " + this.name()) {
                @Override
                public boolean apply(Driver driver, final WebElement element) {
                    return $(element).find(byXpath(".//i[contains(@class, 'anticon')]"))
                            .has(cssClass(iconClass));
                }

                public String actualValue(Driver driver, final WebElement element) {
                    return Arrays.stream(Status.values())
                                 .filter(status -> status.reached.apply(driver, element))
                                 .findFirst()
                                 .map(Enum::toString)
                                 .orElse("UNKNOWN");
                }

                @Override
                public String toString() {
                    return iconClass;
                }
            };
        }
    }

    public static class ShareWith implements AccessObject<ShareWith> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(ADD_USER, context().find(byCssSelector(".anticon-user-add")).closest("button")),
                entry(ADD_GROUP, context().find(byCssSelector(".anticon-usergroup-add")).closest("button")),
                entry(OK, context().find(button("OK")))
        );

        public void addGroupToShare(final String groupName) {
            click(ADD_GROUP);
            SelenideElement selectGroupPopup = Utils.getPopupByTitle("Select group");
            setValue(selectGroupPopup.$(byClassName("ant-select-search__field")), groupName).enter();
            selectGroupPopup.find(button("OK")).shouldBe(visible).click();
            click(OK);
        }

        public void addUserToShare(final String userName, boolean sshConnection) {
            click(ADD_USER);
            SelenideElement selectUserPopup = Utils.getPopupByTitle("Select user");
	    setValue(selectUserPopup.$(byClassName("ant-select-search__field")), userName).enter();
            selectUserPopup.find(button("OK")).shouldBe(visible).click();
            if (sshConnection) {
                checkEnableSShConnection(userName);
            }
            click(OK);
        }

        public void selectEnableSShConnection(final String name) {
            checkEnableSShConnection(name);
            click(OK);
        }

        public void checkEnableSShConnection(final String userName) {
            $(byXpath(".//div[@class='ant-table-content']")).$$(byText(userName)).first().closest("td")
                    .find(By.xpath("following-sibling::td[.='Enable SSH connection']")).parent().click();
        }

        public void removeUserFromShare(final String userName) {
            context().$(byXpath(".//div[@class='ant-table-content']")).$$(byText(userName)).first().parent().parent()
                    .parent().find("button").shouldBe(visible).click();
            click(OK);
        }

        @Override
        public SelenideElement context() {
            return Utils.getPopupByTitle("Share with users and groups");
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}
