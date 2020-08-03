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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static java.lang.String.format;

public class SystemLoggingTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation {

    private static final String TYPE = "security";

    @Test
    @TestCase(value = {"EPMCMBIBPC-3162"})
    public void userAuthentication() {
        logoutIfNeeded();
        loginAs(user);
        logout();
        loginAs(admin);
        SettingsPageAO.SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemLogs();
        SelenideElement adminInfo = systemLogsAO.getInfoRow(format("Successfully authenticate user: %s", admin.login),
                admin.login, TYPE);
        SelenideElement userInfo = systemLogsAO.getInfoRow(format("Successfully authenticate user: %s", user.login),
                user.login, TYPE);
        systemLogsAO.validateTimeOrder(adminInfo, userInfo);
    }
}
