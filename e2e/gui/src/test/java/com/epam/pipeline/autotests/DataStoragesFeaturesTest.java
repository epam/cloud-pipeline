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
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationPermission;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.ALLOW_MOUNT;
import static com.epam.pipeline.autotests.ao.Primitive.DISABLE_MOUNT;
import static com.epam.pipeline.autotests.ao.Primitive.GENERATE_URL;
import static com.epam.pipeline.autotests.ao.Primitive.MOUNT_OPTIONS;
import static com.epam.pipeline.autotests.ao.Primitive.MOUNT_POINT;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static org.testng.Assert.assertFalse;

public class DataStoragesFeaturesTest extends AbstractBfxPipelineTest implements Authorization, Navigation {
    private final String storage = "deactGenUrl-storage-" + Utils.randomSuffix();
    private final String storageMount = "disableMount-storage-" + Utils.randomSuffix();
    private final String storageMount2 = "disableMount-storage2-" + Utils.randomSuffix();
    private final String pipeline2354 = "tool-parameters-2354-" + Utils.randomSuffix();
    private final String configuration2354 = "configuration-2354-" + Utils.randomSuffix();
    private boolean[] storageAllowSignedUrlsState;
    private final String storageAllowSignedUrls = "storage.allow.signed.urls";
    private final String fileName = "file1";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;

    @BeforeClass
    public void createPresetStorage() {
        library()
                .createStorage(storageMount2)
                .createStorage(storage)
                .selectStorage(storage)
                .createFileWithContent(fileName, fileName);
        Stream.of(storageMount2, storage).forEach(storage -> {
            addAccountToStoragePermissions(user, storage);
            givePermissions(user,
                    BucketPermission.allow(READ, storage),
                    BucketPermission.allow(WRITE, storage),
                    BucketPermission.allow(EXECUTE, storage)
            );
        });
        storageAllowSignedUrlsState = navigationMenu()
                .settings()
                .switchToPreferences()
                .getCheckboxPreferenceState(storageAllowSignedUrls);
        library()
                .createPipeline(pipeline2354)
                .createConfiguration(configuration2354);
        addAccountToPipelinePermissions(user, pipeline2354);
        givePermissions(user,
                PipelinePermission.allow(READ, pipeline2354),
                PipelinePermission.allow(WRITE, pipeline2354),
                PipelinePermission.allow(EXECUTE, pipeline2354)
        );
        addAccountToConfigurationPermissions(user, configuration2354);
        givePermissions(user,
                ConfigurationPermission.allow(READ, configuration2354),
                ConfigurationPermission.allow(WRITE, configuration2354),
                ConfigurationPermission.allow(EXECUTE, configuration2354)
        );
    }

    @AfterClass
    public void cleanUp() {
        loginAsAdminAndPerform(() -> {
            library()
                    .removeStorage(storage)
                    .removeStorage(storageMount2)
                    .removeStorageIfExists(storageMount)
                    .removePipeline(pipeline2354)
                    .removeConfiguration(configuration2354);
            if ("false".equals(C.AUTH_TOKEN)) {
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .setCheckboxPreference(storageAllowSignedUrls, storageAllowSignedUrlsState[0], true)
                        .saveIfNeeded();
            }
        });
    }

    @Test
    @TestCase(value = {"TC-DATASTORAGE-1"})
    public void deactivateDownloadFileOption() {
        if ("true".equals(C.AUTH_TOKEN)) {
            assertFalse(storageAllowSignedUrlsState[0],
                    storageAllowSignedUrls + "has 'Enable' value");
        } else {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setCheckboxPreference(storageAllowSignedUrls, false, true)
                    .saveIfNeeded();
        }
        logout();
        loginAs(user);
        library()
                .selectStorage(storage)
                .markCheckboxByName(fileName)
                .ensure(GENERATE_URL, not(visible));
    }

    @Test
    @TestCase(value = {"2354_1"})
    public void checkDisableMountingStorageForToolAndLaunchForm() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .clickOnCreateStorageButton()
                .clickEnableVersioningCheckbox()
                .ensure(DISABLE_MOUNT, visible, enabled)
                .ensureVisible(MOUNT_POINT, MOUNT_OPTIONS, ALLOW_MOUNT)
                .click(DISABLE_MOUNT)
                .ensureNotVisible(MOUNT_POINT, MOUNT_OPTIONS, ALLOW_MOUNT)
                .setStoragePath(storageMount)
                .ok();
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .selectDataStoragesToLimitMounts()
                                .clearSelection()
                                .searchStorage(storageMount)
                                .validateNotFoundStorage()
                                .cancel()
                                .runWithCustomSettings()
                                .selectDataStoragesToLimitMounts()
                                .clearSelection()
                                .searchStorage(storageMount)
                                .validateNotFoundStorage()
                                .cancel()
                );
    }

    @Test
    @TestCase(value = {"2354_2"})
    public void checkDisableMountingStorageForPipelineAndDetachedConfiguration() {
        try {
            logoutIfNeeded();
            loginAs(admin);
            library()
                    .selectStorage(storageMount2)
                    .clickEditStorageButton()
                    .ensure(DISABLE_MOUNT, visible, enabled, have(not(cssClass("ant-checkbox-checked"))))
                    .click(DISABLE_MOUNT)
                    .ensureNotVisible(MOUNT_POINT, MOUNT_OPTIONS, ALLOW_MOUNT)
                    .clickSaveButton();
            logout();
            loginAs(user);
            library()
                    .clickOnPipeline(pipeline2354)
                    .firstVersion()
                    .configurationTab()
                    .click(advancedTab)
                    .editConfiguration("default", profile ->
                            profile.selectDataStoragesToLimitMounts()
                                    .clearSelection()
                                    .searchStorage(storageMount2)
                                    .validateNotFoundStorage()
                                    .cancel()
                    );
            library()
                    .configurationWithin(configuration2354, configuration ->
                            configuration
                                    .expandTabs(advancedTab)
                                    .selectDataStoragesToLimitMounts()
                                    .clearSelection()
                                    .searchStorage(storageMount2)
                                    .validateNotFoundStorage()
                                    .cancel()
                    );
        } finally {
            logoutIfNeeded();
        }
    }
}
