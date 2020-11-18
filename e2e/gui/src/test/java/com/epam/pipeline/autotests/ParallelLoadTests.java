/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.lang.System.setProperty;

public class ParallelLoadTests extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization {

    private static final String PARALLEL_TEST_FOLDER = "parallelTestFolder-" + Utils.randomSuffix();
    private static final Object[][] userList;

    static {
        String propFilePath = getProperty(C.CONF_PATH_PROPERTY, "parallelLoad.conf");
        Properties conf = new Properties();

        try {
            conf.load(new FileInputStream(propFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int confSize = conf.size();
        if (confSize == 0 || confSize % 2 != 0) {
            throw new IllegalArgumentException("parallelLoad.conf is empty or does not contain even number of values");
        }
        userList = IntStream.rangeClosed(1, confSize / 2)
                .mapToObj(i -> new Object[] {
                        conf.getProperty("e2e.ui.login" + i),
                        conf.getProperty("e2e.ui.pass" + i)})
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "openNewBrowser", parallel = true)
    public static Object[][] openNewBrowser() {
        return userList;
    }

    @BeforeTest
    public void addUsers() {
        setUp();
        loginAs(admin);
        Arrays.stream(userList)
            .forEach(this::addUser);
        final String userRoleGroup = "ROLE_USER";
        library()
                .createFolder(PARALLEL_TEST_FOLDER)
                .clickOnFolder(PARALLEL_TEST_FOLDER)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewGroup(userRoleGroup)
                .selectByName(userRoleGroup)
                .showPermissions()
                .set(READ, ALLOW)
                .set(WRITE, ALLOW)
                .set(EXECUTE, ALLOW)
                .closeAll();
        getWebDriver().close();
    }

    @AfterTest(alwaysRun=true)
    public static void closeDriverObjects() {
        getWebDriver().quit();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        Configuration.timeout = C.DEFAULT_TIMEOUT;
        Configuration.browser = WebDriverRunner.CHROME;
        Configuration.startMaximized = true;
        setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        Selenide.open(C.ROOT_ADDRESS);
    }

    @AfterClass(alwaysRun=true)
    public void cleanUp(){
        open(C.ROOT_ADDRESS);
        loginAs(admin);
        library()
                .removeFolder(PARALLEL_TEST_FOLDER);
    }

    @Test(dataProvider = "openNewBrowser", threadPoolSize = 10)
    public void parallelLoadTest(String name, String pass) {
        Account testUser = new Account(name, pass);
        loginAs(testUser);
        long testStartTime = currentTimeMillis();
        for (int i = 1; i <= 10; i++) {
            String runId;
            long startTime = currentTimeMillis();
            navigationMenu()
                    .library()
                    .cd(PARALLEL_TEST_FOLDER);
            executionTime("Open library", name, startTime);
            startTime = currentTimeMillis();
            navigationMenu()
                    .runs();
            executionTime("Open active runs", name, startTime);
            startTime = currentTimeMillis();
            navigationMenu()
                    .runs()
                    .completedRuns();
            executionTime("Open completed runs", name, startTime);
            startTime = currentTimeMillis();
            tools()
                    .perform(C.DEFAULT_REGISTRY, C.DEFAULT_GROUP, C.TESTING_TOOL_NAME, ToolTab::runWithCustomSettings)
                    .launch(this)
                    .showLog(runId = getLastRunId())
                    .shouldHaveRunningStatus();
            executionTime("Running tool", name, startTime);

            startTime = currentTimeMillis();
            runsMenu()
                    .stopRun(runId);
            executionTime("Stop tool", name, startTime);
            startTime = currentTimeMillis();
            navigationMenu()
                    .runs()
                    .completedRuns();
            executionTime("Open completed runs after launch", name, startTime);
        }
        executionTime("Summary time", name, testStartTime);
    }

    private void addUser(Object[] user) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .createIfNotExist(user[0].toString());
    }

    private void executionTime(String action, String user, long startTime) {
        out.println(String.format("%s (%s) : execution time %s ms", action, user, (currentTimeMillis() - startTime)));
    }
}
