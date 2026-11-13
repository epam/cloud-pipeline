/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Selenide.open;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertTrue;

public class PlatformPreferencesTest extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization {

    private static final String CLUSTER_AWS_EBS_TYPE = "cluster.aws.ebs.type";
    private static final String INITIALIZE_NODE = "InitializeNode";
    private static final String INITIALIZE_SHARED_FS = "InitializeSharedFS";
    private static final String INITIALIZE_ENVIRONMENT = "InitializeEnvironment";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String clusterSettingForm = "Cluster";

    @Test
    @TestCase(value = {"897"})
    public void checkHelpContent() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToUserInterface()
                .checkSupportTemplate(C.SUPPORT_CONTENT);
    }

    @Test
    @TestCase(value = {"1489"})
    public void checkLustreMountOption() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLustreFS()
                .checkLustreFSMountOptionsValue(C.LUSTRE_MOUNT_OPTIONS);
    }

    @Test
    @TestCase(value = {"223"})
    public void checkLaunchSystemParameters() {
        final String launchConfig = Utils.readFile(C.LAUNCH_SYSTEM_PARAMETERS_CONFIG_PATH);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .checkLaunchSystemParameters(launchConfig);
    }

    @Test
    @TestCase(value = {"783"})
    public void checkLaunchContainerCpuResource() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .checkLaunchContainerCpuResource(C.LAUNCH_CONTAINER_CPU_RESOURCES_VALUE);
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-1"})
    public void checkClusterAllowedInstanceTypes() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .checkClusterAllowedInstanceTypes(C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES);
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-2"})
    public void checkClusterAllowedInstanceTypesDocker() {
	navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .checkClusterAllowedInstanceTypesDocker(C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER);
    }

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = {"TC-PARAMETERS-3"})
    public void checkConfigureClusterAwsEBSvolumeType() {
        final SettingsPageAO.PreferencesAO.ClusterTabAO clusterTabAO = navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster();
        clusterTabAO
                .searchPreference(CLUSTER_AWS_EBS_TYPE);
        clusterTabAO
                .checkClusterAwsEbsType(C.DEFAULT_CLUSTER_AWS_EBS_TYPE);
        final Set<String> logMess = tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .doNotMountStoragesSelect(true)
		.setDisk("60")
                .setPriceType(ON_DEMAND)
                .createNewNode()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .waitForTask(INITIALIZE_NODE)
                .click(taskWithName(INITIALIZE_NODE))
                .logMessages()
                .collect(toSet());
        checkClusterAwsEBSvolumeTypeInLog(logMess);
    }

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = {"3404"})
    public void allowToSpecifyLustreFSTypeAndThoughput() {
        logout();
        loginAs(user);
        String lustreRunId = null;
        try {
            final RunsMenuAO runs = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .enableClusterLaunch()
                    .clusterSettingsForm(clusterSettingForm)
                    .clusterEnableCheckboxSelect("Enable GridEngine")
                    .ok()
                    .expandTab(ADVANCED_PANEL)
                    .setPriceType(ON_DEMAND)
                    .clickAddSystemParameter()
                    .selectSystemParameters("CP_CAP_SHARE_FS_TYPE",
                            "CP_CAP_SHARE_FS_THROUGHPUT",
                            "CP_CAP_SHARE_FS_SIZE",
                            "CP_CAP_SHARE_FS_DEPLOYMENT_TYPE")
                    .ok()
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_TYPE", "lustre")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_THROUGHPUT", "500")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_SIZE", "1200")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_DEPLOYMENT_TYPE", "PERSISTENT_2")
                    .launch(this);
            lustreRunId = getLastRunId();
            final LogAO logAO = runs
                    .showLog(lustreRunId)
                    .waitForSshLink()
                    .waitForTask(INITIALIZE_SHARED_FS)
                    .waitForTaskStatus(INITIALIZE_SHARED_FS, SUCCESS)
                    .clickTaskWithName(INITIALIZE_SHARED_FS);

            final Set<String> logMess = logAO.logMessages().collect(toSet());
            logAO
                    .logContainsMessage(logMess, "Creating LustreFS with parameters: " +
                            "?size=1200&type=PERSISTENT_2&throughput=500")
                    .logContainsMessage(logMess, "Successfully mounted Lustre FS to master node")
                    .waitForTask(INITIALIZE_ENVIRONMENT)
                    .waitForTaskStatus(INITIALIZE_ENVIRONMENT, SUCCESS);
        } finally {
            stopRunAsOwner(lustreRunId);
            logout();
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = {"3676"})
    public void allowToSpecifyLustreFSmetadataIOPS() {
       // logoutIfNeeded();
        loginAs(user);
        String lustreRunId = null;
        try {
            final RunsMenuAO runs = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .enableClusterLaunch()
                    .clusterSettingsForm(clusterSettingForm)
                    .clusterEnableCheckboxSelect("Enable GridEngine")
                    .ok()
                    .expandTab(ADVANCED_PANEL)
                    .setPriceType(ON_DEMAND)
                    .clickAddSystemParameter()
                    .selectSystemParameters("CP_CAP_SHARE_FS_TYPE",
                            "CP_CAP_SHARE_FS_THROUGHPUT",
                            "CP_CAP_SHARE_FS_SIZE",
                            "CP_CAP_SHARE_FS_DEPLOYMENT_TYPE",
                            "CP_CAP_SHARE_FS_IOPS")
                    .ok()
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_TYPE", "lustre")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_THROUGHPUT", "500")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_SIZE", "1200")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_DEPLOYMENT_TYPE", "PERSISTENT_2")
                    .inputSystemParameterValue("CP_CAP_SHARE_FS_IOPS", "1500")
                    .launch(this);
            lustreRunId = getLastRunId();
            final LogAO logAO = runs
                    .showLog(lustreRunId)
                    .waitForSshLink()
                    .waitForTask(INITIALIZE_SHARED_FS)
                    .waitForTaskStatus(INITIALIZE_SHARED_FS, SUCCESS)
                    .clickTaskWithName(INITIALIZE_SHARED_FS);

            final Set<String> logMess = logAO.logMessages().collect(toSet());
            logAO
                    .logContainsMessage(logMess, "Creating LustreFS with parameters: " +
                            "?size=1200&type=PERSISTENT_2&throughput=500&iops=1500")
                    .logContainsMessage(logMess, "Successfully mounted Lustre FS to master node")
                    .waitForTask(INITIALIZE_ENVIRONMENT)
                    .waitForTaskStatus(INITIALIZE_ENVIRONMENT, SUCCESS);
        } finally {
            stopRunAsOwner(lustreRunId);
            logout();
           // loginAs(admin);
        }
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-4"})
    public void nodeMemoryLimits() {
        String command = "stress -m 1 --vm-bytes 7G --vm-hang 300";
        // The node reporter logs the kernel's record. Newer kernels (Amazon Linux 2023, 6.1) write it in one line
        // that starts with "Memory cgroup out of memory: " ("Out of memory: " for the node's memory), and add
        // ", UID:<uid> pgtables:<N>kB oom_score_adj:<N>" at its end; the older ones wrote "Killed process" first
        String logMessage = "\\[WARN\\] (Memory cgroup out of memory: |Out of memory: )?" +
                "Killed process \\d* \\(stress\\) " +
                "total-vm:\\d*kB, anon-rss:\\d*kB, file-rss:\\d*kB, shmem-rss:\\d*kB";
        String[] messages = {
                "stress: FAIL: \\[\\d*\\] \\(\\d*\\) <-- worker \\d* got signal 9",
                "stress: FAIL: \\[\\d*\\] \\(\\d*\\) failed run completed in \\d*s"};
        try {
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .expandTab(ADVANCED_PANEL)
                    .setTypeValue("c6i.xlarge")
                    .setPriceType(ON_DEMAND)
                    .doNotMountStoragesSelect(true)
                    .launchTool(this, Utils.nameWithoutGroup(tool))
                    .showLog(getLastRunId())
                    .waitForSshLink()
                    .sshInNewTab(shell -> shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute("yum install -y stress")
                            .assertPackageInstallationCompleted(format("pipeline-%s", getLastRunId()))
                            .execute(command)
                            .waitUntilTextAppearsSeveralTimes(getLastRunId(), 3)
                            .assertPageContainsStringsWithRegex(command, messages))
                    .shouldHaveRunningStatus();
            final LogAO logAO = runsMenu()
                    .showLog(getLastRunId())
                    .waitForTask("OOM Logs")
                    .clickTaskWithName("OOM Logs");
            final Set<String> logMess = logAO.logMessages().collect(toSet());
            assertTrue(logMess.stream()
                    .anyMatch(Pattern.compile(logMessage)
                            .asPredicate()), format("Message '%s' isn't found", logMessage));
        } finally {
            open(C.ROOT_ADDRESS);
        }
    }

    /**
     * Stops the run while its non-admin owner is logged in: the active runs page of the admin lists only
     * the admin's own runs, so the cleanup of the class does not stop runs of other users.
     */
    private void stopRunAsOwner(final String runId) {
        if (runId == null) {
            return;
        }
        try {
            open(C.ROOT_ADDRESS);
            runsMenu().stopRunIfPresent(runId);
        } catch (Throwable e) {
            System.out.printf("Run %s could not be stopped: %s%n", runId, e.getMessage());
        }
    }

    private void checkClusterAwsEBSvolumeTypeInLog(Set<String> logMess) {
        assertTrue(logMess.stream()
                .anyMatch(Pattern.compile(format("The requested EBS volume type for \\D+ device is %s",
                        C.DEFAULT_CLUSTER_AWS_EBS_TYPE)).asPredicate()),
                "The requested EBS volume type is absent in the log" );
    }
}
