/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.ShellThemeSettingsAO.Theme.DEFAULT;
import static com.epam.pipeline.autotests.ao.ShellThemeSettingsAO.Theme.LIGHT;
import static java.lang.String.format;

/**
 * The theme settings of the SSH web terminal (the gear icon of the terminal page).
 * <p>
 * The theme a user selects there is kept in the browser (the local storage of the edge host), and each test class
 * starts its own browser with an empty profile, so the case starts with the theme of the user's profile.
 */
public class SshTerminalThemeTest extends AbstractSinglePipelineRunningTest implements Authorization, Tools {

    private static final String[] ALL_THEMES = {"Light", "Default", "Dracula", "Solarized dark", "Solarized light",
        "Gruvbox dark", "Nord", "One dark", "Monokai"};

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
    }

    @Test
    @TestCase(value = {"TC-TEST-ENVIRONMENT-4"})
    public void sshTerminalThemeSettings() {
        // Preparations. The terminal reads the theme of the user's profile each time it connects; the relogin
        // the case asks for is for the GUI.
        open(C.ROOT_ADDRESS);
        if (navigationMenu().settings().switchToMyProfile().selectSshTerminalThemeIfNeeded(LIGHT.label)) {
            logoutIfNeeded();
            loginAs(admin);
        }
        // Steps 1-6
        open(C.ROOT_ADDRESS);
        loginAs(admin);
        tools().perform(registry, group, tool, tool -> tool.run(this));
        final String prompt = format("pipeline-%s", getRunId());
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                // Steps 7-11; the terminal tab is closed after them (step 12)
                .sshInNewTab(shell -> shell
                        .assertPageContains(prompt)
                        .ensureTheme(LIGHT)
                        .ensureThemeSettingsIconInTopRightCorner()
                        .openThemeSettings()
                        .ensureAllFieldsShown()
                        .ensureSelectedTheme(LIGHT)
                        .ensureColors(LIGHT)
                        .ensurePreview(LIGHT)
                        .ensureResetToDefaultsHidden()
                        .clickThemeField()
                        .ensureThemesListed(ALL_THEMES)
                        .selectTheme(DEFAULT)
                        .ensureColors(DEFAULT)
                        .ensurePreview(DEFAULT)
                        .ensureResetToDefaultsShown()
                        .close()
                        .ensureTheme(DEFAULT))
                // Steps 13-16
                .sshInNewTab(shell -> shell
                        .assertPageContains(prompt)
                        .ensureTheme(DEFAULT)
                        .openThemeSettings()
                        .ensureColors(DEFAULT)
                        .ensurePreview(DEFAULT)
                        .resetToDefaults()
                        .ensureColors(LIGHT)
                        .ensurePreview(LIGHT)
                        .ensureResetToDefaultsHidden()
                        .close()
                        .ensureTheme(LIGHT));
        // After: the run is stopped by the cleanup of the class
    }
}
