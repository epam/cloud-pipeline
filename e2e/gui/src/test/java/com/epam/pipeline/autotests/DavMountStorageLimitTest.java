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

import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Supplier;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.DISABLE;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_SYSTEM_ACCESS;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DavMountStorageLimitTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private final String storageWebdavAccessDuration = "storage.webdav.access.duration.seconds";
    private final String uiPipeFileBrowserRequest = "ui.pipe.file.browser.request";
    private final String storageDavMountMaxStorages = "storage.dav.mount.max.storages";
    private final String storage1 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String storage2 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String storage3 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String endpoint = format("%s%s", C.WEBDAV_ADDRESS, user.login.toUpperCase());
    private final int durationSeconds = 60;
    final String userRoleGroup = C.ROLE_USER;
    private String[] webdavAccessDurationInitial;
    private String[] uiPipeFileBrowserRequestInitial;
    private String[] davMountMaxStoragesInitial;

    @BeforeClass
    public void setPreferences() {
        library()
                .createStorage(storage1)
                .createStorage(storage2)
                .createStorage(storage3);
        webdavAccessDurationInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(storageWebdavAccessDuration);
        davMountMaxStoragesInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(storageDavMountMaxStorages);
        uiPipeFileBrowserRequestInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(uiPipeFileBrowserRequest);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(storageWebdavAccessDuration, String.valueOf(durationSeconds), true)
                .setPreference(storageDavMountMaxStorages, "2", true)
                .saveIfNeeded();
    }

    @BeforeMethod
    void openApplication() {
        logoutIfNeeded();
        loginAs(admin);
    }

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        logoutIfNeeded();
        loginAs(admin);
        Utils.removeStorages(this, storage1);
    }

    @AfterClass(alwaysRun = true)
    public void fallBackPreferences() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
            .settings()
            .switchToPreferences()
            .setPreference(storageWebdavAccessDuration,
                    webdavAccessDurationInitial[0],
                    parseBoolean(webdavAccessDurationInitial[1]))
            .setPreference(storageDavMountMaxStorages,
                    davMountMaxStoragesInitial[0],
                    parseBoolean(davMountMaxStoragesInitial[1]))
            .saveIfNeeded();
    }

    @Test
    @TestCase(value = {"2233_1"})
    public void checkWebdavAccessDurationPreference() {
        try {
            library()
                .selectStorage(storage1)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewGroup(userRoleGroup)
                .selectByName(userRoleGroup)
                .showPermissions()
                .set(READ,ALLOW)
                .set(WRITE,ALLOW)
                .closeAll();
            logout();
            loginAs(user);
            library()
                .selectStorage(storage1)
                .showMetadata()
                .ensure(FILE_SYSTEM_ACCESS, enabled)
                .click(FILE_SYSTEM_ACCESS)
                .ensure(FILE_SYSTEM_ACCESS, text("File system access enabled till"))
                .ensure(DISABLE, enabled)
                .sleep(10, SECONDS)
                .inAnotherTab(webdavpage ->
                        checkWebDavPage(() -> new ToolPageAO(endpoint)
                                .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                .assertIndexContains(storage1, true),
                                endpoint));
            sleep(durationSeconds, SECONDS);
            library()
                .selectStorage(storage1)
                .showMetadata()
                .ensure(FILE_SYSTEM_ACCESS, enabled)
                .inAnotherTab(webdavpage ->
                        checkWebDavPage(() -> new ToolPageAO(endpoint)
                                        .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                        .assertIndexContains(storage1, false),
                                endpoint));
        } finally {
            library()
                .selectStorage(storage1)
                .showMetadata()
                .disableFileSystemAccessIfNeeded();
            library()
                .selectStorage(storage1)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .deleteIfPresent(userRoleGroup)
                .closeAll();
        }
    }

    @Test
    @TestCase(value = {"2233_2"})
    public void checkMaxNumberOfStoragesWithWebdavAccess() {
        try {
            library()
                    .selectStorage(storage1)
                    .clickEditStorageButton()
                    .clickOnPermissionsTab()
                    .addNewGroup(userRoleGroup)
                    .selectByName(userRoleGroup)
                    .showPermissions()
                    .set(READ,ALLOW)
                    .set(WRITE,ALLOW)
                    .closeAll();
            logout();
            loginAs(user);
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .ensure(FILE_SYSTEM_ACCESS, enabled)
                    .click(FILE_SYSTEM_ACCESS)
                    .ensure(FILE_SYSTEM_ACCESS, text("File system access enabled till"))
                    .ensure(DISABLE, enabled)
                    .sleep(10, SECONDS)
                    .inAnotherTab(webdavpage ->
                            checkWebDavPage(() -> new ToolPageAO(endpoint)
                                            .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                            .assertIndexContains(storage1, true),
                                    endpoint));
            sleep(durationSeconds, SECONDS);
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .ensure(FILE_SYSTEM_ACCESS, enabled)
                    .inAnotherTab(webdavpage ->
                            checkWebDavPage(() -> new ToolPageAO(endpoint)
                                            .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                            .assertIndexContains(storage1, false),
                                    endpoint));
        } finally {
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .disableFileSystemAccessIfNeeded();
            library()
                    .selectStorage(storage1)
                    .clickEditStorageButton()
                    .clickOnPermissionsTab()
                    .deleteIfPresent(userRoleGroup)
                    .closeAll();
        }
    }

    @Test
    @TestCase(value = {"2233_3"})
    public void checkDisableLink() {
        try {
            library()
                    .selectStorage(storage1)
                    .clickEditStorageButton()
                    .clickOnPermissionsTab()
                    .addNewUser(user.login)
                    .selectByName(user.login)
                    .showPermissions()
                    .set(READ, ALLOW)
                    .set(WRITE, ALLOW)
                    .closeAll();
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .click(FILE_SYSTEM_ACCESS)
                    .ensure(FILE_SYSTEM_ACCESS, text("File system access enabled till"))
                    .ensure(DISABLE, enabled)
                    .sleep(10, SECONDS);
            logout();
            loginAs(user);
            library()
                    .inAnotherTab(webdavpage ->
                            checkWebDavPage(() -> new ToolPageAO(endpoint)
                                            .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                            .assertIndexContains(storage1, true),
                                    endpoint));
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .ensure(DISABLE, enabled)
                    .click(DISABLE)
                    .sleep(10, SECONDS)
                    .inAnotherTab(webdavpage ->
                            checkWebDavPage(() -> new ToolPageAO(endpoint)
                                            .assertPageTitleIs(format("Index of /webdav/%s", user.login.toUpperCase()))
                                            .assertIndexContains(storage1, false),
                                    endpoint));
        } finally {
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .disableFileSystemAccessIfNeeded();
            library()
                    .selectStorage(storage1)
                    .clickEditStorageButton()
                    .clickOnPermissionsTab()
                    .deleteIfPresent(user.login)
                    .closeAll();
        }
    }

    @Test
    @TestCase(value = {"2233_4"})
    public void checkRemoveAllButton() {
        library()
                .selectStorage(storage1)
                .showMetadata()
                .addKeyWithValue("1", "2")
                .click(FILE_SYSTEM_ACCESS)
                .ensure(FILE_SYSTEM_ACCESS, text("File system access enabled till"))
                .deleteAllKeys()
                .ensureTitleIs("Do you want to delete all metadata?")
                .ok()
                .assertNumberOfKeysIs(0)
                .ensure(DISABLE, enabled)
                .click(DISABLE);
    }

    @Test
    @TestCase(value = {"2233_5"})
    public void checkHelpTooltip() {
        uiPipeFileBrowserRequestInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(uiPipeFileBrowserRequest);
        try {


        } finally {

        }
    }

    private void checkWebDavPage(final Supplier<?> webdavpage, final String ipHyperlink) {
        open(ipHyperlink);
        webdavpage.get();
    }


}
