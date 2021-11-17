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
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.lang.String.format;

public class DavMountStorageLimitTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private final String storageWebdavAccessDurationSeconds = "storage.webdav.access.duration.seconds";
    private final String storage1 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private String webdavAccessDurationInitial = "";

    @BeforeClass
    public void setPreferences() {
        library()
                .createStorage(storage1);
    }

    @Test
    @TestCase(value = {"2233_1"})
    public void checkWebdavAccessDurationPreference() {
        webdavAccessDurationInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(storageWebdavAccessDurationSeconds);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(storageWebdavAccessDurationSeconds, "20", true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        library()
                .selectStorage(storage1)
                .showMetadata();
        try {

        } finally {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(storageWebdavAccessDurationSeconds, webdavAccessDurationInitial, true)
                    .saveIfNeeded();
        }
    }

}
