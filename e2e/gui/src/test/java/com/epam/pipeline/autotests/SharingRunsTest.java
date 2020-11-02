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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.FRIENDLY_URL;
import static com.epam.pipeline.autotests.ao.Primitive.SERVICES;
import static com.epam.pipeline.autotests.utils.C.LOGIN;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SharingRunsTest extends AbstractSeveralPipelineRunningTest implements Authorization, Tools {
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String friendlyURL = "tool_page1";
    private String runID = "";
    private String errorMessage = "Url '{\"path\":\"%s\"}' is already used for run '%s'.";
    private String endpointsLink = "";
    private String endpointsName = "";
    private String userGroup = "ROLE_USER";
    private int timeout = C.SHARING_TIMEOUT;

    @Test
    @TestCase({"EPMCMBIBPC-2674"})
    public void validationOfFriendlyURL() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setValue(FRIENDLY_URL, friendlyURL)
                .launch(this)
                .showLog(runID = getLastRunId())
                .waitForEndpointLink()
                .sleep(1, MINUTES)
                .clickOnEndpointLink()
                .sleep(3, SECONDS)
                .validateEndpointPage(LOGIN)
                .assertURLEndsWith(friendlyURL)
                .closeTab();
        runsMenu()
                .log(getLastRunId(), log -> {
                        endpointsLink = log.getEndpointLink();
                        endpointsName = log.getEndpointName();
                });
    }

    @Test(dependsOnMethods = {"validationOfFriendlyURL"})
    @TestCase({"EPMCMBIBPC-2677"})
    public void validationOfFriendlyURLNegative() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setValue(FRIENDLY_URL, friendlyURL)
                .checkLaunchErrorMessage(format(errorMessage,
                        friendlyURL, format("%,d", Integer.parseInt(runID))));
    }

    @Test(dependsOnMethods = {"validationOfFriendlyURL"})
    @TestCase({"EPMCMBIBPC-2678"})
    public void shareToolRunWithUser() {
        try {
            runsMenu()
                    .showLog(runID)
                    .shareWithUser(user.login)
                    .validateShareLink(user.login);
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            loginAs(user);
            sleep(timeout, SECONDS);
            open(endpointsLink);
            new ToolPageAO(endpointsLink)
                    .validateEndpointPage(user.login)
                    .assertURLEndsWith(friendlyURL);
            open(C.ROOT_ADDRESS);
            logout();
            loginAs(admin);
            runsMenu()
                    .showLog(runID)
                    .removeShareUserGroup(user.login)
                    .sleep(2, SECONDS)
                    .validateShareLink("Not shared (click to configure)");
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            loginAs(user);
            sleep(timeout, SECONDS);
            open(endpointsLink, "", user.login, user.password);
            new ToolPageAO(endpointsLink)
                    .assertPageTitleIs("401 Authorization Required");
        } finally {
            open(C.ROOT_ADDRESS);
            logout();
            loginAs(admin);
        }
    }

    @Test(dependsOnMethods = {"validationOfFriendlyURL"})
    @TestCase({"EPMCMBIBPC-2679"})
    public void shareToolRunWithUserGroup() {
        try {
            runsMenu()
                    .log(getLastRunId(), log -> log
                            .shareWithGroup(userGroup)
                            .validateShareLink(userGroup.toLowerCase())
                    );
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            loginAs(user);
            sleep(timeout, SECONDS);
            open(endpointsLink);
            new ToolPageAO(endpointsLink)
                    .validateEndpointPage(user.login)
                    .assertURLEndsWith(friendlyURL);
            open(C.ROOT_ADDRESS);
            logout();
            loginAs(admin);
            runsMenu()
                    .showLog(runID)
                    .removeShareUserGroup(userGroup)
                    .sleep(2, SECONDS)
                    .validateShareLink("Not shared (click to configure)");
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            loginAs(user);
            sleep(timeout, SECONDS);
            open(endpointsLink, "", user.login, user.password);
            new ToolPageAO(endpointsLink)
                    .assertPageTitleIs("401 Authorization Required");
        } finally {
            open(C.ROOT_ADDRESS);
            logout();
            loginAs(admin);
        }
    }

    @Test(dependsOnMethods = {"validationOfFriendlyURL"})
    @TestCase({"EPMCMBIBPC-2680"})
    public void displayingSharingToolAtServicesPanel() {
        try {
            runsMenu()
                    .showLog(runID)
                    .shareWithUser(user.login)
                    .validateShareLink(user.login);
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            sleep(timeout, SECONDS);
            loginAs(user);
            home()
                    .configureDashboardPopUpOpen()
                    .markCheckboxByName("Services")
                    .ok()
                    .ensureVisible(SERVICES)
                    .checkEndpointsLinkOnServicesPanel(endpointsName)
                    .checkServiceToolPath(endpointsName, registry, group, Utils.nameWithoutGroup(tool), runID)
                    .openEndpointLink(endpointsLink, runID)
                    .validateEndpointPage(user.login)
                    .assertURLEndsWith(friendlyURL)
                    .closeTab();
            logout();
            loginAs(admin);
            runsMenu()
                    .showLog(runID)
                    .removeShareUserGroup(user.login)
                    .sleep(3, SECONDS)
                    .validateShareLink("Not shared (click to configure)");
            logout();
            Utils.restartBrowser(C.ROOT_ADDRESS);
            loginAs(user);
            sleep(timeout, SECONDS);
            home()
                    .configureDashboardPopUpOpen()
                    .markCheckboxByName("Services")
                    .ok()
                    .ensureVisible(SERVICES)
                    .checkNoServicesLabel();
        } finally {
            open(C.ROOT_ADDRESS);
            logout();
            loginAs(admin);
        }
    }

}
