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
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.GENERATE_URL;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;

public class DataStoragesFeaturesTest extends AbstractBfxPipelineTest implements Authorization, Navigation {
    private final String storage = "deactGenUrl-storage-" + Utils.randomSuffix();
    private boolean[] storageAllowSignedUrlsState;
    private final String storageAllowSignedUrls = "storage.allow.signed.urls";
    private final String fileName = "file1";

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

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        loginAsAdminAndPerform(() -> {
            if (storageAllowSignedUrlsState != null) {
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .setCheckboxPreference(storageAllowSignedUrls, storageAllowSignedUrlsState[0],
                                storageAllowSignedUrlsState[1])
                        .saveIfNeeded();
            }
            library()
                    .removeStorageIfExists(storage);
        });
    }

    @Test
    @TestCase(value = {"TC-DATASTORAGES-1"})
    public void deactivateDownloadFileOption() {
        // the preference stays visible to users: a non-admin user does not receive hidden preferences
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
                // The actions of the selection: a single one is a button, several are the items of the "Selection"
                // dropdown. With signed URLs allowed, "Generate URL" would be one of several, so it is checked in the
                // opened dropdown, or next to the single "Clear selection" button
                .openSelectionActions()
                .ensure(CLEAR_SELECTION, visible)
                .ensure(GENERATE_URL, not(exist));
    }
}


