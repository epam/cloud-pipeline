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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.SupportButtonAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.SupportButton;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import java.util.List;

import static com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO.UserInterfaceAO.SUPPORT_TEMPLATE;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class PlatformPreferencesTest extends AbstractBfxPipelineTest implements Navigation, Authorization {

    private static final String SUPPORT_ICONS_JSON = "/supportIcons.json";

    @Test
    @TestCase(value = {"897"})
    public void checkHelpContent() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToUserInterface()
                .checkSupportTemplate(C.SUPPORT_CONTENT);
    }

    @Test
    @TestCase(value = {"1489"})
    public void checkLustreMountOption() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLustreFS()
                .checkLustreFSMountOptionsValue(C.LUSTRE_MOUNT_OPTIONS);
    }

    @Test
    @TestCase(value = {"223"})
    public void checkLaunchSystemParameters() {
        final String launchConfig = Utils.readFile(C.LAUNCH_SYSTEM_PARAMETERS_CONFIG_PATH);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .checkLaunchSystemParameters(launchConfig);
    }

    @Test
    @TestCase(value = {"783"})
    public void checkLaunchContainerCpuResource() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .checkLaunchContainerCpuResource(C.LAUNCH_CONTAINER_CPU_RESOURCES_VALUE);
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-1"})
    public void checkClusterAllowedInstanceTypes() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .checkClusterAllowedInstanceTypes(C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES);
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-2"})
    public void checkClusterAllowedInstanceTypesDocker() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .checkClusterAllowedInstanceTypesDocker(C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER);
    }

    @Test
    @TestCase(value = {"2356"})
    public void checkSeveralSupportIconsConfiguration() {
        final SettingsPageAO.PreferencesAO preferencesAO = navigationMenu()
                .settings()
                .switchToPreferences();
        final String supportTemplateValue = preferencesAO
                .switchToUserInterface()
                .getSupportTemplate();
        try {
            final String json = readResourceFully(SUPPORT_ICONS_JSON);
            preferencesAO
                    .setPreference(SUPPORT_TEMPLATE, json, true)
                    .saveIfNeeded()
                    .refresh();
            sleep(2, SECONDS);
            final SupportButtonAO supportButtonAO = new SupportButtonAO();
            final List<SupportButton.Icon> icons = Json.stringToSupportButtons(json).getIcons();
            assertEquals(icons.size(), 3);
            final Condition iconCondition1 = Condition.cssClass(format("anticon-%s", icons.get(0).getIcon()));
            supportButtonAO
                    .checkSupportButtonIcon(iconCondition1);
            final Condition iconCondition2 = Condition.attribute("src", icons.get(1).getIcon());
            supportButtonAO
                    .checkSupportButtonIcon(iconCondition2);
            supportButtonAO.checkSupportButtonContent(icons.get(0), iconCondition1);
            supportButtonAO.checkSupportButtonContent(icons.get(1), iconCondition2);

            logoutIfNeeded();
            loginAs(user);
            sleep(10, SECONDS);
            final Condition iconCondition3 = Condition.cssClass(format("anticon-%s", icons.get(2).getIcon()));
            supportButtonAO
                    .checkSupportButtonIcon(iconCondition3);
            supportButtonAO.checkSupportButtonContent(icons.get(2), iconCondition3);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(SUPPORT_TEMPLATE, supportTemplateValue, true)
                    .saveIfNeeded();
        }
    }
}
