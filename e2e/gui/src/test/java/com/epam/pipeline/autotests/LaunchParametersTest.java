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

import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.SystemParameter;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import java.util.Arrays;

public class LaunchParametersTest extends AbstractAutoRemovingPipelineRunningTest {

    @Test
    @TestCase(value = {"2342_1"})
    public void checkSystemParametersForSpecificUsersGroups() {
        final String launchParametersPreference = SettingsPageAO.PreferencesAO.LaunchAO.LAUNCH_PARAMETERS;
        final String launchSystemParameters = navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .getLaunchSystemParameters();
        final SystemParameter[] systemParameters = Json.stringToSystemParameters(launchSystemParameters);
        final SystemParameter[] systemParameterList = Arrays.stream(systemParameters)
                .peek(p -> {
                    if ("CP_FSBROWSER_ENABLED".equals(p.getName())) {
                        p.setRoles(new String[] {"ROLE_ADMIN", "ROLE_ADVANCED_USER"});
                    }
                })
                .toArray(SystemParameter[]::new);
        String systemParametersToString = Json.systemParametersToString(systemParameterList);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(launchParametersPreference, systemParametersToString, true)
                .saveIfNeeded();
    }
}
