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

import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.ADD_NOTIFICATION;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_ALL_NOTIFICATIONS;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_ALL_RECIPIENTS;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURE_NOTIFICATION;
import static com.epam.pipeline.autotests.ao.Primitive.RECIPIENTS;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NFSQuotasTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation {

    private final String nfsPrefix = C.NFS_PREFIX.replace(":/", "");
    private final String storage = "epmcmbi-test-nfs-" + Utils.randomSuffix();
//    private String storage = "fs-2a5ab373.efs.eu-central-1.amazonaws.com:/quotas_test";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String fileName = "test_file1.txt";
    private String commonRunId;

    @BeforeClass
    public void createNfsStorage() {
        navigationMenu()
                .library()
                .createNfsMount("/" + storage, storage);
        library()
                .selectStorage(storage)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                BucketPermission.allow(READ, storage),
                BucketPermission.allow(WRITE, storage));
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        commonRunId = getLastRunId();
    }

    @AfterClass(alwaysRun = true)
    public void removeStorage() {
        navigationMenu()
                .library()
                .selectStorage(storage)
                .clickEditStorageButton()
                .editForNfsMount()
                .clickDeleteStorageButton()
                .clickDelete();
    }

    @Test
    @TestCase(value = {""})
    public void validateFSMountConfigureNotificationsForm() {
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .ensureVisible(RECIPIENTS, ADD_NOTIFICATION, CLEAR_ALL_NOTIFICATIONS, CLEAR_ALL_RECIPIENTS);
    }

    @Test
    @TestCase(value = {""})
    public void validateFSMountConfigureNotificationsFormForUser() {
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .ensureNotVisible(CONFIGURE_NOTIFICATION)
                .validateConfigureNotificationFormForUser();
        logout();
    }

    @Test
    @TestCase(value = {"2182_3"})
    public void validateFSMountConfigureNotifications() {
        final String disabledMountStatus = "Mount is disabled";
        final String storageSizeWithUnit = format("%s Gb", "1.*");

        loginAs(user);
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        final String userRunId = getLastRunId();
        logout();
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .addRecipient(user.login)
                .addNotification(String.valueOf(1), "Disable mount")
                .addNotification(String.valueOf(2), "Make read-only")
                .ok()
                .checkConfiguredNotificationsLink(2, 1);

        runsMenu()
                .log(commonRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(commonRunId)
                                                .execute(format(
                                                        "head -c 1500MB /dev/urandom > /cloud-data/%s/%s/test1.big",
                                                        nfsPrefix, storage))
                                                .waitUntilTextAppearsSeveralTimes(commonRunId, 2)
                                                .sleep(2, SECONDS))
                                ));
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(disabledMountStatus);

        runsMenu()
                .log(commonRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(commonRunId)
                                                .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                        storage, fileName))
                                                .waitUntilTextAppearsSeveralTimes(commonRunId, 2)
                                                .execute(format("ls -la /cloud-data/%s/%s/", nfsPrefix, storage))
                                                .assertOutputContains(fileName)
                                                .sleep(2, SECONDS))
                                ));
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(disabledMountStatus);

        new StorageContentAO()
                .rmFile(fileName)
                .validateElementNotPresent(fileName);

        runsMenu()
                .log(userRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(userRunId)
                                                .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                        storage, fileName))
                                                .waitUntilTextAppearsSeveralTimes(userRunId, 2)
                                                .assertOutputContains("Read-only file system")
                                                .sleep(2, SECONDS))
                                ));
        tools()
                .perform(registry, group, tool, tool -> tool.run(this))
                .log(getLastRunId(), log -> log.waitForSshLink()
                        .inAnotherTab(logTab -> logTab
                                .ssh(shell -> shell
                                        .waitUntilTextAppears(getLastRunId())
                                        .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                storage, fileName))
                                        .waitUntilTextAppearsSeveralTimes(getLastRunId(), 2)
                                        .assertOutputContains("Read-only file system")
                                        .sleep(2, SECONDS))
                        ))
                .stopRun(getLastRunId());
        logout();
    }
}
