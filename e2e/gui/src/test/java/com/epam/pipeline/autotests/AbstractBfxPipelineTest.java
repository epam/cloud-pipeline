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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.AuthenticationPageAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.io.TemporaryFilesystem;
import org.testng.ITest;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Method;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractBfxPipelineTest implements ITest {

    protected String methodName = "";

    @BeforeClass
    public void setUp() {
        Configuration.timeout = C.DEFAULT_TIMEOUT;
        Configuration.browser = WebDriverRunner.CHROME;
        Configuration.startMaximized = true;
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        login(C.ROOT_ADDRESS);

        //reset mouse
        $(byId("navigation-button-logo")).shouldBe(visible).click();
        sleep(3, SECONDS);
    }

    @BeforeMethod(alwaysRun = true)
    public void setMethodName(Method method, Object[] testData) {
        if (method.isAnnotationPresent(TestCase.class)) {
            final TestCase testCaseAnnotation = method.getAnnotation(TestCase.class);
            for (final String testCase : testCaseAnnotation.value()) {
                this.methodName = String.format("%s - %s", method.getName(), testCase);
            }
        } else {
            this.methodName = method.getName();
        }
    }

    public void restartBrowser(final String address) {
        Selenide.close();
        login(address);
    }

    public void addExtension(final String extensionPath) {
        final ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addExtensions(new File(extensionPath));
        WebDriver webDriver = new ChromeDriver(options);
        WebDriverRunner.setWebDriver(webDriver);
    }

    @Override
    public String getTestName() {
        return this.methodName;
    }

    @AfterMethod(alwaysRun = true)
    public void logging(ITestResult result) {
        StringBuilder testCasesString = new StringBuilder();

        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        if (method.isAnnotationPresent(TestCase.class)) {
            TestCase testCaseAnnotation = method.getAnnotation(TestCase.class);
            for (String testCase : testCaseAnnotation.value()) {
                testCasesString.append(testCase).append(" ");
            }
        }

        System.out.println(String.format("%s::%s [ %s]: %s",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getMethod().getMethodName(),
                testCasesString.toString(),
                getStatus(result.getStatus()))
        );
    }

    @AfterClass(alwaysRun=true)
    public static void tearDownAfterClass() {
        final TemporaryFilesystem tempFS = TemporaryFilesystem.getDefaultTmpFS();
        tempFS.deleteTemporaryFiles();
        getWebDriver().quit();
    }

    private String getStatus(final int numericStatus) {
        String status;
        switch (numericStatus) {
            case 1:
                status = "SUCCESS";
                break;
            case 3:
                status = "SKIP";
                break;
            default:
                status = "FAILS";
                break;
        }
        return status;
    }

    private void login(final String address) {
        WebDriverRunner.getWebDriver().manage().deleteAllCookies();
        if ("true".equals(C.AUTH_TOKEN)) {
            if ("true".equalsIgnoreCase(C.IMPERSONATE_AUTH)) {
                final String suffixPathToNotRedirect = "restapi";
                final String URI = address.endsWith("/")
                        ? address + suffixPathToNotRedirect
                        : address + "/" + suffixPathToNotRedirect;
                Selenide.open(URI);
                Cookie cookie = new Cookie("SESSION", C.PASSWORD, "/pipeline/");
                WebDriverRunner.getWebDriver().manage().addCookie(cookie);
            } else {
                Selenide.open(address);
                Cookie cookie = new Cookie("HttpAuthorization", C.PASSWORD);
                WebDriverRunner.getWebDriver().manage().addCookie(cookie);
            }
        }
        Selenide.open(address);

        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Something wrong with robot", e);
        }
        robot.keyPress(122);
        robot.keyRelease(122);

        if ("false".equals(C.AUTH_TOKEN)) {
            new AuthenticationPageAO()
                    .login(C.LOGIN)
                    .password(C.PASSWORD)
                    .signIn();
        }
        sleep(3, SECONDS);
    }
}
