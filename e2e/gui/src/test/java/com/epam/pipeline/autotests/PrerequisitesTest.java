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

import static com.codeborne.selenide.Condition.visible;
import com.codeborne.selenide.ElementsCollection;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static com.epam.pipeline.autotests.utils.Utils.writeTempJsonFile;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Selenide.$;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PrerequisitesTest extends AbstractBfxPipelineTest implements Navigation, Authorization {
    private static final String uiRunsFiltersJsonInitial = "/uiRunsFiltersInitial.json";
    private static final String uiRunsFiltersJson = "/uiRunsFilters.json";

    @Test
    public void prerequisitesTest() {}

    @Test
    public void muteTestUsersNotifications() {
        Stream.of(admin, user, userWithoutCompletedRuns)
                .forEach(user -> {
                    logout();
                    loginAs(user);
                    closeNotificationIfNeeded();
                    navigationMenu()
                            .settings()
                            .switchToMyProfile()
                            .muteEmailNotificationsSelect(true);
                });
    }

    @Test
    public void setUiRunsFilters() {
        logoutIfNeeded();
        loginAs(admin);
        String[] json = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference("ui.runs.filters");
        writeTempJsonFile(uiRunsFiltersJsonInitial, json[0]);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText("ui.runs.filters",
                        readResourceFully(uiRunsFiltersJson), true)
                .saveIfNeeded();
    }

    private void closeNotificationIfNeeded() {
        if ($(byId("notification-center"))
                .find(byText("hide")).is(visible)) {
            $(byId("notification-center"))
                    .find(byText("hide")).click();
        } else {
            ElementsCollection list = $(byId("notification-center"))
                    .findAll(byClassName("cp-notification"))
                    .filterBy(visible);
            IntStream.range(0, list.size()).forEach(i ->
                    list.get(i).$(byId("notification-close-button")).click());
        }
    }
}
