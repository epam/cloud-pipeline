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

import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.DISABLE;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_SYSTEM_ACCESS;
import static com.epam.pipeline.autotests.ao.Primitive.INFORMATION_ICON;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DavMountStorageLimitTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String UI_PIPE_FILE_BROWSER_JSON = "/uiPipeFileBrowserRequest.json";
    private final String storageWebdavAccessDuration = "storage.webdav.access.duration.seconds";
    private final String uiPipeFileBrowserRequest = "ui.pipe.file.browser.request";
    private final String storageDavMountMaxStorages = "storage.dav.mount.max.storages";
    private final String storage1 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String storage2 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String storage3 = format("davMountLimitStorage%s", Utils.randomSuffix());
    private final String endpoint = format("%s%s", C.WEBDAV_ADDRESS, user.login.toUpperCase());
    private final String errorMessage = "Max value for dav mounted storages is reached!" +
            " Can't request this storage to be mounted, increase quotas!";
    private final String requestInfo = "Description of the Request Filesystem access feature";
    private final String doneRequestInfo = "Help tips - how to use the Filesystem access";
    private final String indexOfWebdav = "Index of /webdav/%s";
    private final String fileSystemaccessEnabled = "File system access enabled till";
    private final String uiPipeFileBrowserJson = readResourceFully(UI_PIPE_FILE_BROWSER_JSON);
    private final int durationSeconds = 60;
    private final String userRoleGroup = C.ROLE_USER;

    @BeforeClass
    public void createStorages() {
        Stream.of(storage1, storage2, storage3)
                .forEach(stor -> library()
                    .createStorage(stor));
    }

    @BeforeMethod
    void reLogin() {
        logoutIfNeeded();
        loginAs(admin);
    }

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        Utils.removeStorages(this, storage1, storage2, storage3);
    }

    @Test
    @TestCase(value = {"2233_1"})
    public void checkWebdavAccessDurationPreference() {
        final String[] webdavAccessDurationInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(storageWebdavAccessDuration);
        try {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(storageWebdavAccessDuration, String.valueOf(durationSeconds), true)
                    .saveIfNeeded();
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
                .ensure(FILE_SYSTEM_ACCESS, text(accessEnabledMessage()))
                .ensure(DISABLE, enabled)
                .sleep(10, SECONDS)
                .inAnotherTab(webdavpage ->
                        checkWebDavPage(() -> new ToolPageAO(endpoint)
                                .assertPageTitleIs(format(indexOfWebdav, user.login.toUpperCase()))
                                .assertIndexContains(storage1, true),
                                endpoint));
            sleep(durationSeconds, SECONDS);
            library()
                .selectStorage(storage1)
                .showMetadata()
                .ensure(FILE_SYSTEM_ACCESS, enabled)
                .inAnotherTab(webdavpage ->
                        checkWebDavPage(() -> new ToolPageAO(endpoint)
                                        .assertPageTitleIs(format(indexOfWebdav, user.login.toUpperCase()))
                                        .assertIndexContains(storage1, false),
                                endpoint));
        } finally {
            logout();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(storageWebdavAccessDuration,
                            webdavAccessDurationInitial[0],
                            parseBoolean(webdavAccessDurationInitial[1]))
                    .saveIfNeeded();
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
        final String[] davMountMaxStoragesInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(storageDavMountMaxStorages);
        try {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(storageDavMountMaxStorages, "2", true)
                    .saveIfNeeded();
            Stream.of(storage1, storage2)
                .forEach(stor -> {
                            library()
                                    .selectStorage(stor)
                                    .showMetadata()
                                    .click(FILE_SYSTEM_ACCESS)
                                    .sleep(5, SECONDS);
                        });
            library()
                    .selectStorage(storage3)
                    .showMetadata()
                    .click(FILE_SYSTEM_ACCESS)
                    .messageShouldAppear(errorMessage);
        } finally {
            logout();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setPreference(storageDavMountMaxStorages,
                            davMountMaxStoragesInitial[0],
                            parseBoolean(davMountMaxStoragesInitial[1]))
                    .saveIfNeeded();
            Stream.of(storage1, storage2)
                    .forEach(stor ->
                            library()
                                    .selectStorage(stor)
                                    .showMetadata()
                                    .disableFileSystemAccessIfNeeded());
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
                    .ensure(FILE_SYSTEM_ACCESS, text(fileSystemaccessEnabled))
                    .ensure(DISABLE, enabled)
                    .sleep(10, SECONDS);
            logout();
            loginAs(user);
            library()
                    .inAnotherTab(webdavpage ->
                            checkWebDavPage(() -> new ToolPageAO(endpoint)
                                            .assertPageTitleIs(format(indexOfWebdav, user.login.toUpperCase()))
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
                                            .assertPageTitleIs(format(indexOfWebdav, user.login.toUpperCase()))
                                            .assertIndexContains(storage1, false),
                                    endpoint));
        } finally {
            logout();
            loginAs(admin);
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
                .ensure(FILE_SYSTEM_ACCESS, text(fileSystemaccessEnabled))
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
        final String[] uiPipeFileBrowserRequestInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(uiPipeFileBrowserRequest);
        try {
            setUiPipeFileBrowserRequest(uiPipeFileBrowserJson);
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .hover(INFORMATION_ICON)
                    .messageShouldAppear(requestInfo)
                    .click(FILE_SYSTEM_ACCESS)
                    .hover(INFORMATION_ICON)
                    .messageShouldAppear(doneRequestInfo);
            setUiPipeFileBrowserRequest(format("%s }", uiPipeFileBrowserJson
                    .substring(0, uiPipeFileBrowserJson.indexOf(",\n"))));
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .ensureNotVisible(INFORMATION_ICON)
                    .click(DISABLE)
                    .ensureVisible(INFORMATION_ICON)
                    .hover(INFORMATION_ICON)
                    .messageShouldAppear(requestInfo);
            setUiPipeFileBrowserRequest("");
            library()
                    .selectStorage(storage1)
                    .showMetadata()
                    .ensureNotVisible(INFORMATION_ICON);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .clearAndSetJsonToPreference(uiPipeFileBrowserRequest,
                            uiPipeFileBrowserRequestInitial[0],
                            parseBoolean(uiPipeFileBrowserRequestInitial[1]))
                    .saveIfNeeded();
        }
    }

    private void checkWebDavPage(final Supplier<?> webdavpage, final String ipHyperlink) {
        open(ipHyperlink);
        webdavpage.get();
    }

    private String accessEnabledMessage() {
        return format("%s %s.", fileSystemaccessEnabled,
                LocalDateTime.now().plusSeconds(durationSeconds)
                        .format(ofPattern("dd MMM yyyy, HH:mm")));
    }

    private void setUiPipeFileBrowserRequest(String json) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .clearAndSetJsonToPreference(uiPipeFileBrowserRequest,
                        json, true)
                .saveIfNeeded();
    }
}
