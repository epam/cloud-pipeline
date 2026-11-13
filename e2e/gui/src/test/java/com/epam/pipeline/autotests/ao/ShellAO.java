/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.ShellThemeSettingsAO.Theme;
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
import org.openqa.selenium.Rectangle;

import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static com.epam.pipeline.autotests.utils.Conditions.textColor;
import static com.codeborne.selenide.CollectionCondition.size;
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
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class ShellAO implements AccessObject<ShellAO> {

    /**
     * A package installation (yum) in a fresh run downloads the repository metadata first: the Rocky Linux 8 test
     * images keep no yum cache, and with EPEL it takes about a minute, more than the default timeout.
     */
    private static final long PACKAGE_INSTALLATION_TIMEOUT = MINUTES.toMillis(10);

    /**
     * How far from the top and right edges of the page the gear icon of the theme settings may be (wetty puts it
     * 5 px from the top and 20 px from the right).
     */
    private static final int CORNER_MARGIN = 50;

    public static ShellAO open(String link) {
        Selenide.open(link);
        return open();
    }

    private static ShellAO open() {
        switchTo().frame(0);
        return new ShellAO();
    }

    public ShellAO assertPageContains(String text) {
        context().$(withText(text)).shouldBe(visible);
        return this;
    }

    public ShellAO assertPageContains(String text1, String text2) {
        context().$(withText(text1)).shouldHave(text(text2));
        return this;
    }

    public ShellAO assertPageDoesNotContain(String text) {
        context().$(withText(text)).shouldNotBe(visible);
        return this;
    }

    public ShellAO execute(String command, CharSequence key) {
        sleep(300, MILLISECONDS);
        context().click();
        Utils.sendKeysWithSlashes(command);
        sleep(300, MILLISECONDS);
        actions().sendKeys(key).perform();
        return this;
    }

    public ShellAO execute(String command) {
        return execute(command, Keys.ENTER);
    }

    public ShellAO inputTextToTextEditor(String command) {
        return execute(command, Keys.ESCAPE);
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
       screenshot(format("atrr_%s", Utils.randomSuffix()));
        Arrays.stream(messages)
                .forEach(message -> assertTrue(lastCommandResult(command).contains(message)));
        return this;
    }

    public ShellAO assertPageAfterCommandNotContainsStrings(String command, String... messages) {
        Arrays.stream(messages)
                .forEach(message -> assertFalse(lastCommandResult(command).contains(message)));
        return this;
    }

    public ShellAO assertPageContainsStringsWithRegex(String command, String ... messages) {
        String log = lastCommandResult(command);
        Arrays.stream(messages)
                .forEach(message ->
                        assertTrue(Pattern.compile(message).matcher(log).find(),
                                format("Message '%s' isn't found", message)));
        return this;
    }

    public ShellAO assertResultsCount(String command, String runID, int expectedCount) {
        String results = lastCommandResult(command)
                .replace(format("root@pipeline-%s:/runs/pipeline-%s#", runID, runID), "");
        assertEquals(expectedCount, results.split("\\s+").length);
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

    /**
     * Clears the terminal and waits until only the prompt is shown. The terminal keeps only the rows on the
     * screen in the page, so a command typed next is the first row, and stays on the screen with all its
     * output as long as the output fits on the screen.
     * <p>
     * {@code clear -x} keeps the scrollback. A plain {@code clear} of ncurses 6 (Rocky 8 test images) also erases
     * the scrollback (ESC[3J), and the web terminal can then stay scrolled by part of a row: it does not draw the
     * prompt row in the page, and the prompt is never found.
     */
    public ShellAO clearScreen(final String prompt) {
        execute("clear -x");
        final ElementsCollection shownRows = $$("x-row").filterBy(matchText("\\S"));
        shownRows.shouldHave(size(1), ofMillis(DEFAULT_TIMEOUT));
        shownRows.first()
                .shouldHave(text(prompt), ofMillis(DEFAULT_TIMEOUT))
                .shouldNotHave(text("clear"), ofMillis(DEFAULT_TIMEOUT));
        return this;
    }

    /**
     * Clears the screen, types the command and waits until the prompt is shown again after it. The output of
     * the command must fit on the screen, and must not contain the prompt text.
     */
    public ShellAO executeAndWaitForPrompt(final String command, final String prompt) {
        return executeAndWaitForPrompt(command, prompt, DEFAULT_TIMEOUT);
    }

    public ShellAO executeAndWaitForPrompt(final String command, final String prompt, final long timeoutMs) {
        clearScreen(prompt);
        execute(command);
        return waitForPromptAfterLastCommand(command, prompt, timeoutMs);
    }

    /**
     * Waits until the prompt is shown again after the last line that holds the command. Type the command
     * after {@link #clearScreen}, so that no earlier copy of it is on the screen; for a command that opens
     * a text editor, call this method after the editor is closed.
     */
    public ShellAO waitForPromptAfterLastCommand(final String command, final String prompt) {
        return waitForPromptAfterLastCommand(command, prompt, DEFAULT_TIMEOUT);
    }

    public ShellAO waitForPromptAfterLastCommand(final String command, final String prompt, final long timeoutMs) {
        $$(withText(command)).last()
                .$(byXpath(format("following::x-row[contains(., '%s')]", prompt)))
                .shouldBe(visible, ofMillis(timeoutMs));
        return this;
    }

    /**
     * Returns the text shown after the last occurrence of the command, without line breaks. Earlier runs of
     * the same command and the typed command itself are not part of it. Read it only after the prompt is
     * back, see {@link #executeAndWaitForPrompt} and {@link #waitForPromptAfterLastCommand}. While the
     * command line is on the screen, all its output is on the screen too.
     */
    public String outputOfLastCommand(final String command) {
        final String page = context().text();
        final int commandIndex = page.lastIndexOf(command);
        assertTrue(commandIndex >= 0, format("Command '%s' is not shown in the terminal: "
                + "its output may be longer than the screen", command));
        return page.substring(commandIndex + command.length()).replace("\n", "");
    }

    public ShellAO assertOutputOfLastCommandContains(final String command, final String... messages) {
        final String output = outputOfLastCommand(command);
        Arrays.stream(messages)
                .forEach(message -> assertTrue(output.contains(message),
                        format("The output of '%s' does not contain '%s': %s", command, message, output)));
        return this;
    }

    public ShellAO assertOutputOfLastCommandDoesNotContain(final String command, final String... messages) {
        final String output = outputOfLastCommand(command);
        Arrays.stream(messages)
                .forEach(message -> assertTrue(!output.contains(message),
                        format("The output of '%s' contains '%s': %s", command, message, output)));
        return this;
    }

    public ShellAO assertOutputOfLastCommandMatches(final String command, final String... regexps) {
        final String output = outputOfLastCommand(command);
        Arrays.stream(regexps)
                .forEach(regexp -> assertTrue(Pattern.compile(regexp).matcher(output).find(),
                        format("The output of '%s' does not match '%s': %s", command, regexp, output)));
        return this;
    }

    public ShellAO assertPageContainsString(String str) {
        context().shouldHave(text(str));
        return this;
    }

    public ShellAO assertNextStringIsVisibleAtFileUpload(String str1, String str2) {
        $(withText(str1)).shouldBe(visible).parent()
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", str2))).shouldBe(visible, ofMillis(DEFAULT_TIMEOUT));
        return this;
    }

    public ShellAO assertNextStringIsVisible(String str1, String str2) {
        $(withText(str1)).shouldBe(visible)
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", str2))).shouldBe(visible, ofMillis(DEFAULT_TIMEOUT));
        return this;
    }

    /**
     * Waits until the yum command executed last completes ("Complete!" followed by the prompt), up to
     * {@link #PACKAGE_INSTALLATION_TIMEOUT}.
     */
    public ShellAO assertPackageInstallationCompleted(String prompt) {
        $(withText("Complete!")).shouldBe(visible, ofMillis(PACKAGE_INSTALLATION_TIMEOUT))
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", prompt))).shouldBe(visible, ofMillis(DEFAULT_TIMEOUT));
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

    /**
     * Checks that the gear icon of the theme settings is in the top right corner of the terminal page. The icon is
     * outside the terminal frame.
     */
    public ShellAO ensureThemeSettingsIconInTopRightCorner() {
        switchTo().defaultContent();
        try {
            final Rectangle icon = themeSettingsIcon().shouldBe(visible).getRect();
            final long pageWidth = ((Number) Selenide.executeJavaScript("return window.innerWidth;")).longValue();
            assertTrue(icon.getY() <= CORNER_MARGIN && icon.getX() + icon.getWidth() >= pageWidth - CORNER_MARGIN,
                    format("The gear icon (x %d, y %d, width %d) is not in the top right corner of the page "
                            + "(width %d)", icon.getX(), icon.getY(), icon.getWidth(), pageWidth));
        } finally {
            switchTo().frame(0);
        }
        return this;
    }

    /**
     * Clicks the gear icon of the terminal page and returns its "Theme Settings" pop-up. The icon and the pop-up are
     * outside the terminal frame: {@link ShellThemeSettingsAO#close()} switches back into it.
     */
    public ShellThemeSettingsAO openThemeSettings() {
        switchTo().defaultContent();
        themeSettingsIcon().shouldBe(visible).click();
        return new ShellThemeSettingsAO(this).ensureOpened();
    }

    /**
     * Checks the background and text colors of the terminal against the theme.
     */
    public ShellAO ensureTheme(final Theme theme) {
        $("x-screen").shouldHave(backgroundColor(theme.background), textColor(theme.text));
        return this;
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

    public int getRowsNumber() {
        final String fileMetadata = context().text().substring(context().text().indexOf("\"/tmp/"));
        final Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s+(\\d+)L,\\s+(\\d+)C");
        final Matcher matcher = pattern.matcher(fileMetadata);
        assertTrue(matcher.find(), format("Could not find the number of lines in the file metadata: %s", fileMetadata));
        return Integer.parseInt(matcher.group(2));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }

    /**
     * The gear icon of the theme settings: the wetty page puts it right after the element of the terminal, with no id
     * or class of its own.
     */
    private SelenideElement themeSettingsIcon() {
        return $(byXpath("//div[@id='terminal']/following-sibling::div[.//*[local-name()='svg']]"));
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
