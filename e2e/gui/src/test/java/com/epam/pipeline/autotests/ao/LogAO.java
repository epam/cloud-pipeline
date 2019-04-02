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
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;

public class LogAO implements AccessObject<LogAO> {
    public static final long SSH_LINK_APPEARING_TIMEOUT = C.SSH_APPEARING_TIMEOUT;
    public static final long COMMIT_BUTTON_APPEARING_TIMEOUT = C.COMMIT_APPEARING_TIMEOUT;
    public static final long COMMITTING_TIMEOUT = C.COMMITTING_TIMEOUT;
    public static final long COMPLETION_TIMEOUT = C.COMPLETION_TIMEOUT;
    public static final long BUCKETS_MOUNTING_TIMEOUT = C.BUCKETS_MOUNTING_TIMEOUT;

    private static Condition completed = Condition.or("finished", Status.SUCCESS.reached, Status.STOPPED.reached, Status.FAILURE.reached);

    private final Map<Primitive,SelenideElement> elements = initialiseElements(
            entry(STATUS, $(byClassName("log__run-title"))),
            entry(TITLE, $(byClassName("log__run-title")).find(byClassName("log__pipeline-link"))),
            entry(SSH_LINK, $$(tagName("a")).findBy(exactText("SSH"))),
            entry(COMMIT, $$(tagName("a")).findBy(exactText("COMMIT"))),
            entry(PAUSE, $$(tagName("a")).findBy(exactText("PAUSE"))),
            entry(RESUME, $$(tagName("a")).findBy(exactText("RESUME"))),
            entry(ENDPOINT, $(withText("Endpoint")).closest("tr").find("a")),
            entry(INSTANCE, context().find(byXpath("//*[.//*[text()[contains(.,'Instance')]] and contains(@class, 'ant-collapse')]"))),
            entry(PARAMETERS, context().find(byXpath("//*[.//*[text()[contains(.,'Parameters')]] and contains(@class, 'ant-collapse')]")))
    );

    public LogAO waitForCompletion() {
        get(STATUS).waitUntil(completed, COMPLETION_TIMEOUT);
        return this;
    }

    public LogAO waitFor(final Status expectedStatus) {
        get(STATUS).waitUntil(expectedStatus.reached, COMPLETION_TIMEOUT);
        return this;
    }

    public LogAO waitForSshLink() {
        get(SSH_LINK).waitUntil(appears, SSH_LINK_APPEARING_TIMEOUT);
        return this;
    }

    public ShellAO clickOnSshLink() {
        return ShellAO.open(getSshLink());
    }

    public LogAO ssh(final Consumer<ShellAO> shell) {
        shell.accept(clickOnSshLink());
        return this;
    }

    public LogAO waitForCommitButton() {
        get(COMMIT).waitUntil(visible, COMMIT_BUTTON_APPEARING_TIMEOUT);
        return this;
    }

    public LogAO shouldHaveStatus(Status status) {
        get(STATUS).shouldHave(status.reached);
        return this;
    }

    public Stream<String> logMessages() {
        $(byClassName("log__logs-table")).shouldBe(visible);
        Utils.scrollElementToPosition(".log__logs-table", 0);
        Set<String> lines = new LinkedHashSet<>();
        boolean keepScrolling = true;
        int offset = 0;
        while (keepScrolling) {
            ElementsCollection messages = $(byClassName("log__logs-table")).findAll(byClassName("log__log-row"));
            keepScrolling = lines.addAll(messages.texts());
            offset += messages.stream().mapToInt(element -> element.getSize().getHeight()).sum();
            Utils.scrollElementToPosition(".log__logs-table", offset);
        }
        return lines.stream();
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
        return messageShouldAppear("COMMITTING")
                .messageShouldAppear("COMMIT SUCCEEDED", COMMITTING_TIMEOUT);
    }

    public LogAO waitForPauseButton() {
        get(PAUSE).waitUntil(visible, SSH_LINK_APPEARING_TIMEOUT);
        return this;
    }

    public LogAO clickOnPauseButton() {
        get(PAUSE).shouldBe(visible).click();
        return this;
    }

    public LogAO pause(final String pipelineName) {
        clickOnPauseButton();
        new ConfirmationPopupAO<>(this)
                .ensureTitleIs(
                        String.format("Do you want to pause %s?", pipelineName))
                .sleep(1, SECONDS)
                .click(button(PAUSE.name()));
        return this;
    }

    public LogAO assertPausingFinishedSuccessfully() {
        return assertPausingStatus().waitForResumeButton();
    }

    public LogAO assertPausingStatus() {
        return messageShouldAppear("PAUSING");
    }

    public LogAO waitForResumeButton() {
        get(RESUME).waitUntil(visible, SSH_LINK_APPEARING_TIMEOUT);
        return this;
    }

    public LogAO resume(final String pipelineName) {
        get(RESUME).shouldBe(visible).click();
        new ConfirmationPopupAO<>(this)
                .ensureTitleContains(String.format("Do you want to resume %s?", pipelineName))
                .sleep(2, SECONDS)
                .click(button(RESUME.name()));
        return this;
    }

    public LogAO assertResumingFinishedSuccessfully() {
        return messageShouldAppear("RESUMING").waitForPauseButton();
    }

    public LogAO validateException(final String exception) {
        $(byClassName("ant-alert-error")).has(text(exception));
        return this;
    }

    public LogAO waitForLog(final String message) {
        for (int i = 0; i < 50; i++) {
            refresh();
            if ($(log()).is(matchText(message))) {
                break;
            }
            sleep(20, SECONDS);
        }
        return this;
    }

    public LogAO waitForTask(final String task) {
        $(taskWithName(task)).waitUntil(visible, COMPLETION_TIMEOUT);
        return this;
    }

    public LogAO instanceParameters(final Consumer<InstanceParameters> action) {
        expandTab(INSTANCE);
        action.accept(new InstanceParameters());
        return this;
    }

    public LogAO clickMountBuckets() {
        waitForMountBuckets().closest("a").click();
        return this;
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
     * Selects entry of a parameters panel on a run page.
     *
     * Pay attention there is no need to add any colon, use clean {@code name} and {@code value}.
     * @return Qualifier of a parameter with such {@code name} and {@code value}.
     */
    public static By configurationParameter(final String name, final String value) {
        return byXpath(String.format(".//*/*[normalize-space(translate(., ' \t\n', '   ')) = '%s:%s']", name, value));
    }

    public LogAO validateRunTitle(String title) {
        return ensure(TITLE, text(title));
    }

    public SelenideElement waitForMountBuckets() {
        return $(byXpath("//*[contains(@class, 'ant-menu-item') and .//*[contains(., 'MountDataStorages')]]//*[contains(@class, 'anticon')]"))
                .waitUntil(cssClass("status-icon__icon-green"), BUCKETS_MOUNTING_TIMEOUT);
    }

    public static By runId() {
        return byXpath(".//h1[contains(@class, 'log__run-title')]//*[contains(text(), 'Run #')]");
    }

    public static By pipelineLink() {
        return byXpath(".//h1[contains(@class, 'log__run-title')]//*[@class = 'log__pipeline-link']");
    }

    public static By detailsWithLabel(final String label) {
        Objects.requireNonNull(label);
        return byXpath(String.format("//tr[.//th[normalize-space(text()) = '%s:']]//td", label));
    }

    public static By taskList() {
        return byXpath(".//ul[contains(@class, 'log__task-list')]");
    }

    public static By task() {
        return Combiners.confine(tagName("li"), taskList(), "task");
    }

    public static By taskWithName(final String name) {
        Objects.requireNonNull(name);
        final By taskQualifier = byXpath(String.format(".//li[contains(., '%s')]", name));
        return Combiners.confine(taskQualifier, taskList(), String.format("task with name %s", name));
    }

    public static By parameterWithName(final String name, final String value) {
        Objects.requireNonNull(name);
        return byXpath(String.format(
                "//tr[.//td[contains(@class, 'log__task-parameter-name') and contains(.//text(), '%s')] and " +
                        ".//td[contains(., '%s')]]", name, value));
    }

    public static By log() {
        return byClassName("ReactVirtualized__List");
    }

    public static By logMessage(final String text) {
        Objects.requireNonNull(text);
        final String messageClass = "log__log-row";
        final By messageQualifier = byXpath(String.format(
                "//*[contains(concat(' ', @class, ' '), ' %s ') and .//*[contains(., \"%s\")]]",
                messageClass, text
        ));
        return Combiners.confine(messageQualifier, log(), String.format("log message with text {%s}", text));
    }

    public static By timeInfo(final String label) {
        Objects.requireNonNull(label);
        return byXpath(String.format(
            ".//*[@class = 'task-link__time-info' and contains(.//text(), '%s')]",
            label
        ));
    }

    public static Condition containsMessage(final String text) {
        Objects.requireNonNull(text);
        return new Condition(String.format("contains message {%s}", text)) {

            private static final String container = ".log__logs-table";
            private final By message = logMessage(text);

            @Override
            public boolean apply(final WebElement log) {
                boolean seen = $(log).find(message).is(visible);
                if (!seen) {
                    final Set<String> lines = new LinkedHashSet<>();
                    boolean keepScrolling = true;
                    int offset = 0;
                    Utils.scrollElementToPosition(container, 0);
                    while (!seen && keepScrolling) {
                        final ElementsCollection messages = $(container).findAll(byClassName("log__log-row"));
                        seen = $(message).is(visible);
                        keepScrolling = lines.addAll(messages.texts());
                        offset += messages.stream().mapToInt(element -> element.getSize().getHeight()).sum();
                        Utils.scrollElementToPosition(container, offset);
                    }
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
            public boolean apply(final WebElement logElement) {
                Arrays.stream(texts)
                        .map(String::trim)
                        .filter(expectedText -> !containsMessage(expectedText).apply(logElement))
                        .forEach(missingMessages::add);
                return missingMessages.isEmpty();
            }

            @Override
            public String actualValue(final WebElement logElement) {
                final String allMissingMessages = missingMessages.stream().collect(Collectors.joining("\n"));
                return String.format("Following messages wasn't found in log:%n%s", allMissingMessages);
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
                entry(IP, context().find(parameterWithName("IP")))
        );

        public static By parameterWithName(final String name) {
            final String parameterName = "log__node-parameter-name";
            final String parameterValue = "log__node-parameter-value";
            return byXpath(String.format(
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
        SUCCESS("status-icon__icon-green"),
        FAILURE("status-icon__icon-red"),
        STOPPED("status-icon__icon-yellow"),
        WORKING("status-icon__icon-blue"),
        LOADING("anticon-loading"),
        PAUSED("anticon-pause-circle-o");

        public final Condition reached;

        Status(String iconClass) {
            this.reached = new Condition("status " + this.name()) {
                @Override
                public boolean apply(final WebElement element) {
                    return $(element).find(byXpath(".//i[contains(@class, 'status-icon')]")).has(cssClass(iconClass));
                }

                @Override
                public String actualValue(final WebElement element) {
                    return Arrays.stream(Status.values())
                                 .filter(status -> status.reached.apply(element))
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
}
