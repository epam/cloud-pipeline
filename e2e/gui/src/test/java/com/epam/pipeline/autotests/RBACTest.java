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

import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.screenshot;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RBACTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String toolEndpoint = testingTool.substring(testingTool.lastIndexOf("/") + 1);

    @Test(enabled = false)
    @TestCase(value = "EPMCMBIBPC-3014")
    public void authenticationInPlatform() {
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToCLI()
                .switchGitCLI()
                .ensureCodeHasText(format("git config --global user.name \"%s\"", admin.login));
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3015")
    public void failedAuthentication() {
        Utils.restartBrowser(C.ROOT_ADDRESS);
        final Account wrongAccount = new Account(C.LOGIN, String.format("123%s", C.PASSWORD));
        loginAs(wrongAccount);
        if ("true".equals(C.AUTH_TOKEN)) {
            validateErrorPage("type=Unauthorized, status=401");
            logout();
        } else {
            validateErrorPage("Incorrect user name or password");
        }
        loginAs(admin);
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3169")
    public void checkAnonymousAccess() {
        if ("false".equals(C.AUTH_TOKEN)) {
            return;
        }
        logoutIfNeeded();
        loginAs(admin);
        String endpoint = tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .launchTool(this, toolEndpoint)
                .show(getLastRunId())
                .waitEndpoint()
                .attr("href");
        logout();
        Utils.restartBrowser(endpoint);
        new ShellAO().assertPageContains("type=Unauthorized, status=401").close();
        open(C.ROOT_ADDRESS);
        loginAs(admin);
        runsMenu()
                .log(getLastRunId(), log -> log
                        .shareWithGroup("ROLE_ANONYMOUS_USER")
                        .validateShareLink("role_anonymous_user")
        );
        sleep(1, MINUTES);
        logout();
        final Account anonymousAccount = new Account(C.ANONYMOUS_NAME, C.ANONYMOUS_TOKEN);
        loginAs(anonymousAccount);
        sleep(2, SECONDS);
        screenshot("Endpoint_page");
        open(endpoint);
        new ShellAO().assertPageContains(C.ANONYMOUS_NAME);
        Utils.restartBrowser(C.ROOT_ADDRESS);
        loginAs(admin);
    }
}
