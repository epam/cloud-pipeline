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
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.*;

public class NFSQuotasTest extends AbstractBfxPipelineTest implements Authorization, Navigation {

    private String storage = "epmcmbi-test-nfs-" + Utils.randomSuffix();
//    private String storage = "fs-2a5ab373.efs.eu-central-1.amazonaws.com:/quotas_test";

    @BeforeClass
    public void createNfsStorage() {
        navigationMenu()
                .library()
                .createNfsMount("/" + storage, storage);
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
    @TestCase(value = {""})
    public void validateFSMountConfigureNotifications() {
        navigationMenu()
                .library()
                .selectStorage(storage)
                .showMetadata()
                .configureNotification()
                .addRecipient(user.login)
                .addNotification(String.valueOf(1), "Disable mount")
                .addNotification(String.valueOf(2), "Make read-only")
                .ok();
    }
}
