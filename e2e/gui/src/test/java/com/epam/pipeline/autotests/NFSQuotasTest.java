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

import com.codeborne.selenide.Condition;
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

import java.util.Collections;

import static com.epam.pipeline.autotests.ao.Primitive.ADD_NOTIFICATION;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_ALL_NOTIFICATIONS;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_ALL_RECIPIENTS;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURE_NOTIFICATION;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.RECIPIENTS;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NFSQuotasTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation {

    private static final String TEST_NAME_BIG_FILE_1 = "test1.big";
    private static final String TEST_NAME_BIG_FILE_2 = "test2.big";
    private static final String DISABLED_MOUNT_STATUS = "MOUNT IS DISABLED";
    private static final String READ_ONLY_MOUNT_STATUS = "READ-ONLY";
    private final String nfsPrefix = C.NFS_PREFIX.replace(":/", "");
    private final String storage = "epmcmbi-test-nfs-" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String fileName = "test_file1.txt";
    private final String fileName2 = "test_file2.txt";
    private String commonRunId;
    private String userRunId;
    private String userRunId2;

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
    @TestCase(value = {"2182_1"})
    public void validateFSMountConfigureNotificationsForm() {
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .ensureVisible(RECIPIENTS, ADD_NOTIFICATION, CLEAR_ALL_NOTIFICATIONS, CLEAR_ALL_RECIPIENTS)
                .ensureAll(Condition.enabled, RECIPIENTS, ADD_NOTIFICATION, OK, CANCEL)
                .ensureDisable(CLEAR_ALL_NOTIFICATIONS, CLEAR_ALL_RECIPIENTS)
                .ok();
    }

    @Test(dependsOnMethods = {"validateFSMountConfigureNotificationsForm"})
    @TestCase(value = {"2182_2"})
    public void validateFSMountConfigureNotificationsFormForUser() {
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .validateConfigureNotificationFormForUser();
        logout();
        loginAs(admin);
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .addRecipient(user.login)
                .ensure(CLEAR_ALL_RECIPIENTS, Condition.enabled)
                .addNotification(String.valueOf(1), "Disable mount")
                .addNotification(String.valueOf(2), "Make read-only")
                .ensure(CLEAR_ALL_NOTIFICATIONS, Condition.enabled)
                .ok()
                .checkConfiguredNotificationsLink(2, 1);
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .checkConfiguredNotificationsLink(2, 1)
                .configureNotification()
                .checkConfigureNotificationIsNotAvailable()
                .checkRecipients(Collections.singletonList(user.login))
                .checkNotification(String.valueOf(1), "Disable mount")
                .checkNotification(String.valueOf(2), "Make read-only")
                .click(CANCEL);
        logout();
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .clearAllNotifications()
                .clearAllRecipients()
                .ok()
                .ensure(CONFIGURE_NOTIFICATION, Condition.text("Configure notifications"));
    }

    @Test(dependsOnMethods = {"validateFSMountConfigureNotificationsFormForUser"})
    @TestCase(value = {"2182_3"})
    public void validateFSMountConfigureNotifications() {
        final String storageSizeWithUnit = format("%s Gb", "1.*");

        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        userRunId = getLastRunId();
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
                                                        "head -c 1500MB /dev/urandom > /cloud-data/%s/%s/%s",
                                                        nfsPrefix, storage, TEST_NAME_BIG_FILE_1))
                                                .waitUntilTextAppearsSeveralTimes(commonRunId, 2)
                                                .sleep(2, SECONDS))
                                ));
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .waitUntilStatusUpdated(DISABLED_MOUNT_STATUS)
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(DISABLED_MOUNT_STATUS);

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
                .checkStorageStatus(DISABLED_MOUNT_STATUS);

        new StorageContentAO()
                .rmFile(fileName)
                .validateElementNotPresent(fileName);
        sleep(5, MINUTES);
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

    @Test(dependsOnMethods = {"validateFSMountConfigureNotifications"})
    @TestCase(value = {"2182_4"})
    public void validateReadOnlyQuota() {
        final String storageSizeWithUnit = format("%s Gb", "2.*");
        runsMenu()
                .log(commonRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(commonRunId)
                                                .execute(format(
                                                        "head -c 1000MB /dev/urandom > /cloud-data/%s/%s/%s",
                                                        nfsPrefix, storage, TEST_NAME_BIG_FILE_2))
                                                .waitUntilTextAppearsSeveralTimes(commonRunId, 2)
                                                .sleep(2, SECONDS))
                                ));
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .waitUntilStatusUpdated(READ_ONLY_MOUNT_STATUS)
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(READ_ONLY_MOUNT_STATUS);

        new StorageContentAO()
                .ensureVisible(CREATE, UPLOAD);

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
                .waitUntilStatusUpdated(READ_ONLY_MOUNT_STATUS)
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(READ_ONLY_MOUNT_STATUS);

        new StorageContentAO()
                .ensureNotVisible(CREATE, UPLOAD);

        runsMenu()
                .log(userRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(userRunId)
                                                .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                        storage, fileName2))
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
                                                storage, fileName2))
                                        .waitUntilTextAppearsSeveralTimes(getLastRunId(), 2)
                                        .assertOutputContains("Read-only file system")
                                        .sleep(2, SECONDS))
                        ));
        userRunId2 = getLastRunId();
        logout();
    }

    @Test(dependsOnMethods = {"validateReadOnlyQuota"})
    @TestCase(value = {"2182_5"})
    public void validateQuotaAtBackProcess() {
        final String storageSizeWithUnit = format("%s Gb", "1.*");

        navigationMenu()
                .library()
                .selectStorage(storage)
                .rmFile(TEST_NAME_BIG_FILE_2);

        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .waitUntilStatusUpdated(DISABLED_MOUNT_STATUS)
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(DISABLED_MOUNT_STATUS);

        loginAs(user);

        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .waitUntilStatusUpdated(DISABLED_MOUNT_STATUS)
                .checkWarningStatusIcon()
                .checkStorageSize(storageSizeWithUnit)
                .checkStorageStatus(DISABLED_MOUNT_STATUS);
        new StorageContentAO()
                .ensureVisible(CREATE, UPLOAD);

        runsMenu()
                .log(commonRunId, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(commonRunId)
                                                .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                        storage, fileName))
                                                .waitUntilTextAppearsSeveralTimes(commonRunId, 2)
                                                .assertOutputContains("Read-only file system")
                                                .sleep(2, SECONDS))
                                ));
        logout();

        navigationMenu()
                .library()
                .selectStorage(storage)
                .rmFile(TEST_NAME_BIG_FILE_1);

        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .checkWarningStatusIconNotVisible()
                .checkStorageSize("0");

        loginAs(user);

        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .checkWarningStatusIconNotVisible()
                .checkStorageSize("0");

        new StorageContentAO()
                .ensureVisible(CREATE, UPLOAD);

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
        runsMenu()
                .log(userRunId2, log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(userRunId2)
                                                .execute(format("echo test file >> /cloud-data/%s/%s/%s", nfsPrefix,
                                                        storage, fileName2))
                                                .waitUntilTextAppearsSeveralTimes(userRunId2, 2)
                                                .execute(format("ls -la /cloud-data/%s/%s/", nfsPrefix, storage))
                                                .assertOutputContains(fileName2)
                                                .sleep(2, SECONDS))
                                ));
        logout();
    }
}
