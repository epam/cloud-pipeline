/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.epam.pipeline.autotests.utils.Privilege.*;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RunToolsInSandboxCheckDataStorageTest
        extends AbstractSinglePipelineRunningTest
        implements Authorization, Tools {

    private final String defaultCommand = "/start.sh";
    private final String type = C.DEFAULT_INSTANCE;
    private final String disk = "15";
    private final String price = "Spot";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String bucket1 = "test-storage-1-" + Utils.randomSuffix();
    private final String bucket2 = "test-storage-2-" + Utils.randomSuffix();
    private final String endpoint = C.VALID_ENDPOINT;

    @BeforeClass
    public void createBuckets() {
        library()
                .createStorage(bucket1)
                .createStorage(bucket2);
        addUserForBucket(bucket1, user);
        addUserForBucket(bucket2, user);
        givePermissions(user,
                BucketPermission.allow(READ, bucket1),
                BucketPermission.allow(WRITE, bucket1));
        givePermissions(user,
                BucketPermission.deny(WRITE, bucket2),
                BucketPermission.deny(READ, bucket2));
    }

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallbackToToolDefaultState() {
        fallbackToToolDefaultState(registry,
                group,
                tool,
                endpoint,
                defaultCommand,
                type,
                price,
                disk);
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            library()
                    .removeStorage(bucket1)
                    .removeStorage(bucket2);
        });
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-498"})
    public void checkToolAccessToDataStorage() {
        giveUserPermissionForTool();
        logout();
        loginAs(user);
        final Set<String> log =
                tools()
                        .perform(registry, group, tool, tool ->
                                tool.settings()
                                        .addEndpoint(endpoint)
                                        .setDefaultCommand(defaultCommand)
                                        .save()
                                        .runWithCustomSettings()
                        )
                        .setLaunchOptions(disk, type, null)
                        .launchTool(this, Utils.nameWithoutGroup(tool))
                        .showLog(getRunId())
                        .clickMountBuckets()
                        .logMessages().collect(toSet());

        assertTrue(logContainsMessageWith(log, bucket1));
        assertFalse(logContainsMessageWith(log, bucket2));
    }

    private boolean logContainsMessageWith(final Set<String> log, final String bucket) {
        final String substring = "%s mounted to /cloud-data/%s";
        return log.stream().anyMatch(message -> message.contains(String.format(substring, bucket, bucket)));
    }

    private void giveUserPermissionForTool() {
        tools().performWithin(registry, group, tool, tool ->
                tool.permissions().addNewUser(user.login).closeAll()
        );
        givePermissions(user,
                ToolPermission.allow(WRITE, tool, registry, group),
                ToolPermission.allow(EXECUTE, tool, registry, group)
        );
    }

    private void addUserForBucket(String bucket, Account user) {
        library()
                .selectStorage(bucket)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
    }
}
