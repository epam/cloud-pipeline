/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import static java.lang.String.format;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RestorePreferencesTest extends AbstractBfxPipelineTest implements Navigation, Authorization {

    private static final String uiRunsFiltersJsonInitial = format("%s/uiRunsFiltersInitial.json", C.DOWNLOAD_FOLDER);

    @Test
    public void restoreUiRunsFilters() {
        logoutIfNeeded();
        loginAs(admin);
        try {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .updateCodeText("ui.runs.filters",
                            StringUtils.join(Files.readAllLines(Paths.get(uiRunsFiltersJsonInitial)), "\n"), true)
                    .saveIfNeeded();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
