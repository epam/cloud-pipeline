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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Comparators;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Keys;

import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.codeborne.selenide.Selenide.switchTo;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class ShellAO implements AccessObject<ShellAO> {

    public static ShellAO open(String link) {
        Selenide.open(link);
        return open();
    }

    private static ShellAO open() {
        switchTo().frame(0);
        return new ShellAO();
    }

    public ShellAO assertPageContains(String text) {
        $(withText(text)).shouldBe(visible);
        return this;
    }

    public ShellAO assertPageContains(String text1, String text2) {
        $(withText(text1)).shouldHave(text(text2));
        return this;
    }

    public ShellAO assertPageDoesNotContain(String text) {
        $(withText(text)).shouldNotBe(visible);
        return this;
    }

    public ShellAO execute(String command) {
        sleep(300, MILLISECONDS);
        Utils.sendKeysWithSlashes(command);
        sleep(300, MILLISECONDS);
        actions().sendKeys(Keys.ENTER).perform();
        return this;
    }

    public ShellAO assertOutputContains(String... messages) {
        Arrays.stream(messages)
                .forEach(this::assertPageContains);
        return this;
    }

    public ShellAO assertOutputDoesNotContain(String... messages) {
        Arrays.stream(messages)
                .forEach(this::assertPageDoesNotContain);
        return this;
    }

    public ShellAO assertPageAfterCommandContainsStrings(String command, String... messages) {
        Arrays.stream(messages)
                .forEach(message -> assertTrue(lastCommandResult(command).contains(message)));
        return this;
    }

    public ShellAO assertPageAfterCommandNotContainsStrings(String command, String... messages) {
        Arrays.stream(messages)
                .forEach(message -> assertFalse(lastCommandResult(command).contains(message)));
        return this;
    }

    public ShellAO assertResultsCount(String command, String runID, int expectedCount) {
        String results = lastCommandResult(command)
                .replace(format("[root@pipeline-%s ~]#", runID), "");
        assertEquals(results.split("\\s+").length, expectedCount);
        return this;
    }

    public ShellAO assertFileVersionsCount(String command, String fileName, int expectedCount) {
        String results = lastCommandResult(command);
        int countMatches = StringUtils.countMatches(results, fileName);
        assertEquals(expectedCount, countMatches,
                format("Actual count: %s. Expected count: %s", countMatches, expectedCount));
        return this;
    }

    public String lastCommandResult(String command) {
        return context().text().substring(context().text().indexOf(command))
                .replace("\n", "").replace(command, "");
    }

    public String getFirstLine() {
        return context().getText().substring(0, context().getText().indexOf("\n"));
    }

    public ShellAO assertPageContainsString(String str) {
        context().shouldHave(text(str));
        return this;
    }

    public ShellAO assertNextStringIsVisibleAtFileUpload(String str1, String str2) {
        $(withText(str1)).shouldBe(visible).parent()
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", str2))).shouldBe(visible);
        return this;
    }

    public ShellAO assertNextStringIsVisible(String str1, String str2) {
        $(withText(str1)).shouldBe(visible)
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", str2))).shouldBe(visible);
        return this;
    }

    public NavigationMenuAO assertAccessIsDenied() {
        assertPageContains("Permission denied");
        return close();
    }

    public NavigationMenuAO close() {
        Selenide.open(C.ROOT_ADDRESS);
        return new NavigationMenuAO();
    }

    public ShellAO waitUntilTextAppears(final String runId) {
        waitUntilTextAppears("pipeline", runId);
        return this;
    }

    public ShellAO waitUntilTextAppears(final String pipeName, final String runId) {
        for (int i = 0; i < 2; i++) {
            sleep(10, SECONDS);
            if ($(withText(format("%s-%s", pipeName.toLowerCase(), runId))).exists()) {
                break;
            }
            sleep(1, MINUTES);
            refresh();
            close();
            sleep(5, SECONDS);
            new NavigationMenuAO().runs().showLog(runId).clickOnSshLink();
        }
        return this;
    }

    public ShellAO waitUntilTextLoads(final String runId) {
        for (int i = 0; i < 3; i++) {
            sleep(10, SECONDS);
            if ($(withText(format("pipeline-%s", runId))).exists()) {
                break;
            }
            sleep(1, MINUTES);
            refresh();
            sleep(5, SECONDS);
        }
        return this;
    }

    public ShellAO waitUntilTextAppearsSeveralTimes(final String runId, final int textNumberTimes) {
        for (int i = 0; i < 3; i++) {
            sleep(10, SECONDS);
            if ($$(withText(format("root@pipeline-%s", runId))).size() >= textNumberTimes) {
                break;
            }
            sleep(1, MINUTES);
        }
        return this;
    }

    public ShellAO checkVersionsListIsSorted(String command) {
        List<String> vers = versionsCreationData(command);
        assertTrue(Comparators.isInOrder(vers, Comparator.reverseOrder()));
        return this;
    }

    public ShellAO waitForLog(final String message) {
        for (int i = 0; i < 15; i++) {
            if ($(withText(message)).is(matchText(message))) {
                break;
            }
            sleep(20, SECONDS);
        }
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }

    private List<String> versionsCreationData(String command) {
        String log = lastCommandResult(command);
        List<String> list = new ArrayList<>();
        Matcher matcher = Pattern.compile(" \\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2} ").matcher(log);
        while (matcher.find()) {
            list.add(matcher.group());
        }
        return list;
    }
}
