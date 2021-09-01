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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.GENERATE_URL;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static org.testng.Assert.assertTrue;

public class DataStoragesFeaturesTest extends AbstractBfxPipelineTest implements Authorization, Navigation {
    private String storage = "deactGenUrl-storage-" + Utils.randomSuffix();
    private boolean[] storageAllowSignedUrlsState;
    private String storageAllowSignedUrls = "storage.allow.signed.urls";
    private String fileName = "file1";

    @BeforeClass
    public void createPresetStorage() {
        library()
            .createStorage(storage)
            .selectStorage(storage)
            .createFileWithContent(fileName, fileName);
        addAccountToStoragePermissions(user, storage);
        givePermissions(user,
             BucketPermission.allow(READ, storage),
             BucketPermission.allow(WRITE, storage),
             BucketPermission.allow(EXECUTE, storage)
        );
        storageAllowSignedUrlsState = navigationMenu()
                .settings()
                .switchToPreferences()
                .getCheckboxPreferenceState(storageAllowSignedUrls);
    }

    @AfterClass
    public void cleanUp() {
        loginAsAdminAndPerform(() -> {
            library()
                    .removeStorage(storage);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setCheckboxPreference(storageAllowSignedUrls, storageAllowSignedUrlsState[0], true)
                    .saveIfNeeded();
        });
    }

    @Test
    @TestCase(value = {""})
    public void deactivateDownloadFileOption() {
        if ("true".equals(C.AUTH_TOKEN)) {
            assertTrue(storageAllowSignedUrlsState[0],
                    storageAllowSignedUrls + "has 'Enable' value");
            }
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setCheckboxPreference(storageAllowSignedUrls, false, true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        library()
            .selectStorage(storage)
                .markCheckboxByName(fileName)
                .ensure(GENERATE_URL, not(visible));
    }
}


