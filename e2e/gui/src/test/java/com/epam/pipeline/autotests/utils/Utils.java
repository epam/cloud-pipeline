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

package com.epam.pipeline.autotests.utils;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.RunPipelineTest;
import com.epam.pipeline.autotests.mixins.Navigation;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertTrue;

public class Utils {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static void assertTimePassed(String dateAndTimeString, int maxSeconds) {
        LocalDateTime runDateTime = validateDateTimeString(dateAndTimeString);

        LocalDateTime now = LocalDateTime.now();
        assertTrue(now.toEpochSecond(ZoneOffset.UTC) - runDateTime.toEpochSecond(ZoneOffset.UTC) < maxSeconds);
    }

    public static LocalDateTime validateDateTimeString(String dateTimeString) {
        return LocalDateTime.parse(
                dateTimeString,
                DateTimeFormatter.ofPattern(Utils.DATE_TIME_PATTERN));
    }

    public static String getRunIdWhenOnActiveRunsPage(String pipelineName) {
        SelenideElement row = $("tbody").find(byText(pipelineName)).closest(".ant-table-row");
        ElementsCollection columns = row.findAll("td");

        String pipelineRunId = columns.get(0).text();

        return pipelineRunId.substring(pipelineName.length() + 1);
    }

    public static SelenideElement getPopupByTitle(String title) {
        return $$(byClassName("ant-modal-content")).findBy(text(title));
    }

    public static String readResourceFully(String resourceName) {
        return getResourcesReader(resourceName).lines().collect(Collectors.joining("\n"));
    }

    public static BufferedReader getResourcesReader(String resourceName) {
        return new BufferedReader(new InputStreamReader(
                RunPipelineTest.class.getResourceAsStream(resourceName)));
    }

    public static void scrollElementToPosition(String elementCssSelector, int scrollPosition) {
        Selenide.executeJavaScript("document.querySelector('" + elementCssSelector + "').scrollTop = " + scrollPosition, "");
    }

    public static void clickAndSendKeysWithSlashes(WebElement element, String text) {
        actions().moveToElement(element).click().perform();
        sendKeysWithSlashes(text);
    }

    public static void clearTextField(final SelenideElement field) {
        sleep(500, MILLISECONDS);
        final Actions action = actions().moveToElement(field).click();
        for (int i = 0; i < 1000; i++) {
            action.sendKeys("\b").sendKeys(Keys.DELETE);
        }
        action.perform();
    }

    public static void sendKeysWithSlashes(final String text) {
        //////////////////////////////////////////////////////////////////////////
        // ! WARNING: there is robot to fix forward slashed issue
        // https://sqa.stackexchange.com/questions/25038/selenium-send-keys-on-chromium-confused-by-forward-slashes
        // http://grokbase.com/t/gg/selenium-users/149s9xe7r5/send-keys-and-slash-character
        for (String s : text.split("")) {
            if (s.equals("/")) {
                Robot robot;
                try {
                    robot = new Robot();
                } catch (AWTException e) {
                    throw new RuntimeException("Something wrong with robot", e);
                }
                robot.keyPress('/');
                robot.keyRelease('/');
                continue;
            }
            actions().sendKeys(s).perform();
        }
        //////////////////////////////////////////////////////////////////////////
    }

    public static void sendKeysByChars(final SelenideElement field, final String text) {
        final char[] charArray = text.toCharArray();
        int charNumber = 0;
        for (final char character : charArray) {
            while (true) {
                field.sendKeys(String.valueOf(character));
                sleep(1, SECONDS);
                final String enteredText = field.getAttribute("value");
                sleep(1, SECONDS);
                if (enteredText.charAt(charNumber) == character) {
                    break;
                }
            }
            charNumber++;
        }
    }

    public static String getPipelineRunId(final String pipelineName) {
        return findRunId(new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(className("run-table__run")).stream()
                        .filter(element ->
                                element.findElements(byClassName("run-table__run-row-pipeline")).stream()
                                        .map(WebElement::getText)
                                        .anyMatch(pipelineName -> pipelineName.equals(pipelineName)))
                        .collect(toList());
            }

            @Override
            public String toString() {
                return (pipelineName);
            }
        });
    }

    public static String getToolRunId(final String toolName) {
        final String toolSelfName = nameWithoutGroup(toolName);
        final String nameWithTag = toolSelfName + ":latest";
        return findRunId(new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(className("run-table__run")).stream()
                        .filter(element ->
                                element.findElements(byClassName("run-table__run-row-docker-image")).stream()
                                        .map(WebElement::getText)
                                        .anyMatch(pipelineName -> pipelineName.equals(toolSelfName)
                                                || pipelineName.equals(toolSelfName + ":latest")
                                        )
                        )
                        .collect(toList());
            }

            @Override
            public String toString() {
                return (nameWithTag + " run id");
            }
        });
    }

    public static String getToolRunId(final String toolName, final String tag) {
        final String toolSelfName = nameWithoutGroup(toolName);
        final String nameWithTag = toolSelfName + ":" + tag;
        return findRunId(new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(className("run-table__run")).stream()
                        .filter(element ->
                                element.findElements(byClassName("run-table__run-row-docker-image")).stream()
                                        .map(WebElement::getText)
                                        .anyMatch(pipelineName -> pipelineName.equals(toolSelfName)
                                                || pipelineName.equals(nameWithTag)
                                        )
                        )
                        .collect(toList());
            }

            @Override
            public String toString() {
                return (nameWithTag + " run id");
            }
        });
    }

    private static String findRunId(final By runRowQualifier) {
        final SelenideElement element = $(runRowQualifier).should(exist).find(byClassName("run-table__run-row-name"));
        final String runName = element.text();
        return runNameToRunId(runName);
    }

    private static String runNameToRunId(final String runName) {
        String[] splitRunName = runName.substring(0, runName.indexOf("\n")).split("-");
        return splitRunName[splitRunName.length - 1];
    }

    public static void sleep(long duration, TimeUnit units) {
        Selenide.sleep(MILLISECONDS.convert(duration, units));
    }

    public static long randomSuffix() {
        return Double.doubleToLongBits(Math.random());
    }

    public static File createTempFile() throws RuntimeException {
        return createTempFile("");
    }

    public static File createTempFile(String suffix) throws RuntimeException {
        return createTempFile("new-file-%s-%d.file", suffix);
    }

    public static File createTempFileWithName(final String fileName) throws RuntimeException {
        final File uploadedFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(fileName).toFile();
        try {
            uploadedFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to create temporary file %s, cause: %s", fileName, e.getMessage()), e);
        }
        uploadedFile.deleteOnExit();
        return uploadedFile;
    }

    public static File createTempFile(String template, String suffix) throws RuntimeException {
        String fileName = String.format(template, suffix, randomSuffix());
        File uploadedFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(fileName).toFile();
        try {
            uploadedFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to create temporary file %s, cause: %s", fileName, e.getMessage()), e);
        }
        uploadedFile.deleteOnExit();
        return uploadedFile;
    }

    public static File createTempFileWithExactName(final String fileName) throws RuntimeException {
        final File file = Paths.get(C.DOWNLOAD_FOLDER).resolve(fileName).toFile();
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to create temporary file %s, cause: %s", fileName, e.getMessage()), e);
        }
        file.deleteOnExit();
        return file;
    }

    public static File createTempFileWithNameAndSize(String name) {
        File uploadedFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(name).toFile();
        try {
            FileWriter fileWriter = new FileWriter(uploadedFile.getName(), true);
            BufferedWriter bw = new BufferedWriter(fileWriter);
            bw.write("String to change size");
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to create temporary file %s, cause: %s", name, e.getMessage()), e);
        }
        uploadedFile.deleteOnExit();
        return uploadedFile;
    }

    public static File createFileAndFillWithString(String name,
                                                   String repeatingString,
                                                   int totalNumberOfChars) {

        byte[] content = repeatString("abc1", totalNumberOfChars / repeatingString.length()).getBytes();
        Path path = Paths.get(C.DOWNLOAD_FOLDER).resolve(name);
        try {
            Files.write(path, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        File file = path.toFile();
        file.deleteOnExit();
        return file;
    }

    private static String repeatString(String str, int times) {
        return new String(new char[times]).replace("\0", str);
    }

    public static void removeStorages(Navigation test, String... buckets) {
        Stream.of(buckets)
                .filter(s -> {
                    test.navigationMenu()
                            .library()
                            .sleep(1, SECONDS);
                    return $(byText(s)).exists();
                })
                .forEach(s -> test
                        .navigationMenu()
                        .library()
                        .removeStorage(s));
    }

    public static String getFileNameFromPipelineName(String pipelineName, String suffix) {
        return String.format("%s.%s", pipelineName.replaceAll("-", ""), suffix)
                .toLowerCase();
    }

    public static SelenideElement getFormRowByLabel(SelenideElement context, String label) {
        return context.find(withText(label)).closest(".ant-row");
    }

    public static String convertRGBColorToHex(String folderBackgroundСolor) {
        String[] RGB = folderBackgroundСolor.split(", ");
        Color color = new Color(
                Integer.valueOf(RGB[0].substring(RGB[0].indexOf("(") + 1)),
                Integer.valueOf(RGB[1]),
                Integer.valueOf(RGB[2]));
        String hex = "#" + Integer.toHexString(color.getRGB()).substring(2);
        return hex;
    }

    /**
     * Clear tool name of group if that is present.
     *
     * @param toolName e.g. {@code library/endpoint-test} or {@code endpoint-test}
     * @return In both mentioned above cases it returns {@code endpoint-test}
     */
    public static String nameWithoutGroup(final String toolName) {
        final int indexOfGroupSeparator = toolName.indexOf("/");
        return indexOfGroupSeparator < 0
                ? toolName
                : toolName.substring(indexOfGroupSeparator + 1);
    }

    /**
     * Generates unique {@link String} that can be used as resource name.
     * <p>
     * <strong>NB!</strong> This is not pure function because of {@link #randomSuffix()} usage, so be careful about it.
     *
     * @param testCase An actual test case name in which context this resource is used.
     * @return String that is prefixed with a {@code ui-tests_} identifier that says that this resource has been
     * concerned to particularly UI tests, and is suffixed with a randomly generated number.
     */
    public static String resourceName(final String testCase) {
        return String.format("ui-tests-%s-%d", testCase, Utils.randomSuffix());
    }

    public static void restartBrowser(final String address) {
        Selenide.close();
        Selenide.open(address);
    }

    public static void clearField(By field) {
        $(field).click();
        $(field).sendKeys(Keys.chord(Keys.CONTROL, "a"));
        $(field).sendKeys(Keys.DELETE);
    }
}
