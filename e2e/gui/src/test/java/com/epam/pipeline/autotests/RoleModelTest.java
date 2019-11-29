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

import com.epam.pipeline.autotests.ao.PermissionTabAO;
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.ao.ToolGroup;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.ToolDescription.editButtonFor;
import static com.epam.pipeline.autotests.utils.Privilege.*;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.*;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class RoleModelTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String userGroup = "DOMAIN USERS";
    private final String userRoleGroup = "ROLE_USER";

    private final String pipelineName = "role-model-test-pipeline-" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "sh");
    private final String tempFileName = "tempFileName";

    private final String folderWithSeveralPipelines = "folder-with-several-pipelines-" + Utils.randomSuffix();
    private final String firstOfTheSeveralPipelines = "first-of-the-several-pipelines-" + Utils.randomSuffix();
    private final String secondOfTheSeveralPipelines = "second-of-the-several-pipelines-" + Utils.randomSuffix();

    private final String bucket = "bucket-" + Utils.randomSuffix();
    private final String anotherBucket = "another-bucket-" + Utils.randomSuffix();
    private final String bucketForDataStoragesTests = "role-model-bucket-for-ds-test-" + Utils.randomSuffix();
    private final String presetBucketForDataStoragesTests = "role-model-preset-bucket-for-ds-test-" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String defaultCommand = "/start.sh";

    @Override
    @BeforeClass
    public void setUp() {
        super.setUp();
        navigationMenu()
                .library()
                .createPipeline(Template.SHELL, pipelineName);

        navigationMenu()
                .library()
                .createStorage(bucket)
                .selectStorage(bucket)
                .createFolder("folder-in-bucket");

        navigationMenu()
                .library()
                .createStorage(anotherBucket)
                .selectStorage(anotherBucket)
                .createFolder("folder-in-another-bucket");

        navigationMenu()
                .library()
                .createFolder(folderWithSeveralPipelines)
                .cd(folderWithSeveralPipelines)
                .createPipeline(Template.SHELL, firstOfTheSeveralPipelines)
                .createPipeline(Template.SHELL, secondOfTheSeveralPipelines);

        createGroupPrerequisites();

        logout();
    }

    @Override
    @AfterClass(alwaysRun = true)
    public void removeNodes() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            sleep(3, MINUTES);
            super.removeNodes();
        });
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin)
                    .library()
                    .cd(folderWithSeveralPipelines)
                    .removePipeline(firstOfTheSeveralPipelines)
                    .removePipeline(secondOfTheSeveralPipelines)
                    .removeFolder(folderWithSeveralPipelines);

            navigationMenu()
                    .library()
                    .removeStorage(bucket)
                    .removeStorage(anotherBucket)
                    .removePipeline(pipelineName);

            Utils.removeStorages(this, bucketForDataStoragesTests, presetBucketForDataStoragesTests);
        });
    }

    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        logoutIfNeeded();
        loginAs(admin);
        fallbackToToolDefaultState(registry, group, tool);
    }

    @Test(priority = 0, enabled = false)
    @TestCase({"EPMCMBIBPC-559"})
    public void validateUserWithoutPermission() {
        loginAs(admin);
        discardPermissions();
        logout();
        loginAs(userWithoutCompletedRuns)
                .clusterNodes()
                .assertNodesTableIsEmpty();
        navigationMenu()
                .library()
                .ensure(byText(folderWithSeveralPipelines), not(visible)) // folder user doesn't have access to
                .assertNoPipelinesAreDisplayed();
        tools()
                .ensure(byText("No registries configured"), visible);
        navigationMenu()
                .runs()
                .activeRuns()
                .validateNoRunsArePresent()
                .completedRuns()
                .validateNoRunsArePresent();
    }

    @Test(priority = 1, enabled = false)
    @TestCase({"EPMCMBIBPC-569"})
    public void checkRunHistoryWithoutRunsBefore() {
        logoutIfNeeded();
        loginAs(userWithoutCompletedRuns);
        navigationMenu()
                .runs()
                .completedRuns()
                .validateNoRunsArePresent();
    }

    @Test(priority = 2)
    @TestCase("EPMCMBIBPC-537")
    public void givePermissionsToUser() {
        logoutIfNeeded();
        loginAs(admin);
        getPipelinePermissionsTab(pipelineName)
                .addNewUser(user.login)
                .validateUserHasPermissions(getUserNameByAccountLogin(user.login))
                .closeAll();
    }

    @Test(priority = 3)
    @TestCase({"EPMCMBIBPC-539"})
    public void viewUserPermissions() {
        logoutIfNeeded();
        loginAs(admin);
        getUserPipelinePermissions(user, pipelineName)
                .validateAllPrivilegesAreListed()
                .validateAllCheckboxesAreListed()
                .validatePrivilegeValue(READ, INHERIT)
                .validatePrivilegeValue(WRITE, INHERIT)
                .validatePrivilegeValue(EXECUTE, INHERIT)
                .closeAll();
    }

    @Test(priority = 4)
    @TestCase({"EPMCMBIBPC-540"})
    public void giveUserWritePermissionForPipeline() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user, PipelinePermission.allow(WRITE, pipelineName));
        getUserPipelinePermissions(user, pipelineName)
                .validatePrivilegeValue(WRITE, ALLOW)
                .closeAll();

        //return permissions back to the starting state
        givePermissions(user, PipelinePermission.inherit(WRITE, pipelineName));
    }

    @Test(priority = 5)
    @TestCase({"EPMCMBIBPC-546"})
    public void giveUserReadPermissionForPipeline() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user, PipelinePermission.allow(READ, pipelineName));
        logout();
        loginAs(user)
                .library()
                .validatePipeline(pipelineName);
    }

    @Test(priority = 6)
    @TestCase({"EPMCMBIBPC-547"})
    public void readOnlyUserIsNotAbleToEditPipeline() {
        logoutIfNeeded();
        loginAs(user);
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .ensureNotVisible(RENAME, DELETE, NEW_FILE, UPLOAD, RUN)
                .documentsTab()
                .ensureNotVisible(DELETE, RENAME, UPLOAD)
                .storageRulesTab()
                .ensureNotVisible(DELETE, ADD_NEW_RULE);
    }

    @Test(priority = 7)
    @TestCase({"EPMCMBIBPC-558"})
    public void readOnlyUserIsNotAbleToEditFileInPipeline() {
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .clickOnFile(fileInPipeline)
                .assertFileIsNotEditable()
                .close();
    }

    @Test(priority = 8)
    @TestCase({"EPMCMBIBPC-549"})
    public void checkFolderPermissionForReadOnlyUser() {
        logoutIfNeeded();
        loginAs(admin);
        getFolderPermissionsTab(folderWithSeveralPipelines)
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user, FolderPermission.allow(READ, folderWithSeveralPipelines));
        logout();

        loginAs(user)
                .library()
                .clickOnFolder(folderWithSeveralPipelines)
                .assertPipelineIsNotEditable(firstOfTheSeveralPipelines)
                .assertPipelineIsNotEditable(secondOfTheSeveralPipelines)
                .hover(SETTINGS)
                .ensure(EDIT_FOLDER, not(visible));
    }
    @Test(priority = 9)
    @TestCase({"EPMCMBIBPC-552"})
    public void checkFolderPermissionsForReadOnlyUserAndCheckInnerDeniedPipelinePermissions() {
        logoutIfNeeded();
        loginAs(admin)
                .library()
                .clickOnFolder(folderWithSeveralPipelines)
                .clickOnPipeline(firstOfTheSeveralPipelines)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                PipelinePermission.deny(READ, firstOfTheSeveralPipelines),
                PipelinePermission.deny(WRITE, firstOfTheSeveralPipelines),
                PipelinePermission.deny(EXECUTE, firstOfTheSeveralPipelines));
        logout();

        loginAs(user)
                .library()
                .clickOnFolder(folderWithSeveralPipelines)
                .assertThereIsPipeline(secondOfTheSeveralPipelines)
                .assertThereIsNoPipeline(firstOfTheSeveralPipelines);
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-553"})
    public void checkPermissionsForExecutingPipelines() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user, PipelinePermission.allow(EXECUTE, pipelineName));
        getUserPipelinePermissions(user, pipelineName)
                .validatePrivilegeValue(READ, ALLOW)
                .closeAll();
        logout();

        loginAs(user)
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .runPipeline()
                .waitUntilLaunchButtonAppear()
                .launch(this);

        navigationMenu()
                .clusterNodes()
                .waitForTheNode(pipelineName, getLastRunId());
        navigationMenu()
                .runs()
                .activeRuns()
                .validatePipelineIsPresent(pipelineName)
                .stopRun(getLastRunId());
        clusterMenu()
                .removeNode(getLastRunId());
    }

    @Test(priority = 11)
    @TestCase({"EPMCMBIBPC-560"})
    public void rerunPipelineWithoutPermissionsForExecute() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user,
                PipelinePermission.deny(EXECUTE, pipelineName));
        getPipelinePermissionsTab(pipelineName)
                .selectByName(getUserNameByAccountLogin(user.login))
                .showPermissions()
                .validatePrivilegeValue(EXECUTE, DENY)
                .validatePrivilegeValue(READ, ALLOW)
                .closeAll();
        logout();

        loginAs(user)
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .historyTab()
                .rerun()
                .messageShouldAppear("You have no permissions to launch " + pipelineName);
    }

    @Test(priority = 12)
    @TestCase({"EPMCMBIBPC-556"})
    public void validatePermissionsForEditPipeline() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user,
                PipelinePermission.allow(WRITE, pipelineName));
        getPipelinePermissionsTab(pipelineName)
                .selectByName(getUserNameByAccountLogin(user.login))
                .showPermissions()
                .validatePrivilegeValue(WRITE, ALLOW)
                .validatePrivilegeValue(READ, ALLOW)
                .closeAll();
        logout();
        loginAs(user);
        getFirstVersionOfPipeline(pipelineName)
                .clickOnRenameFileButton(fileInPipeline)
                .typeInField(tempFileName)
                .ok();
        // Rename file to the previous name
        getFirstVersionOfPipeline(pipelineName)
                .ensure(byText(tempFileName), visible)
                .clickOnRenameFileButton(tempFileName)
                .typeInField(fileInPipeline)
                .ok();
    }

    @Test(priority = 13)
    @TestCase({"EPMCMBIBPC-557"})
    public void validatePermissionsForEditPipelineFiles() {
        logoutIfNeeded();
        loginAs(user);
        String newFileContent = "abcd";
        getFirstVersionOfPipeline(pipelineName)
                .clearAndFillPipelineFile(fileInPipeline, newFileContent);
        refresh();
        getFirstVersionOfPipeline(pipelineName)
                .clickOnFile(fileInPipeline)
                .shouldContainInCode(newFileContent)
                .close();
    }

    @Test(priority = 14)
    @TestCase({"EPMCMBIBPC-561"})
    public void editAndExecutePipeline() {
        String newFileContent = "new content";
        logoutIfNeeded();
        loginAs(admin);
        getPipelinePermissionsTab(pipelineName)
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                PipelinePermission.allow(EXECUTE, pipelineName),
                PipelinePermission.allow(WRITE, pipelineName));
        logout();
        loginAs(user)
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .clickOnRenameFileButton(fileInPipeline)
                .typeInField(tempFileName)
                .ok();
        refresh();
        getFirstVersionOfPipeline(pipelineName)
                .ensure(byText(tempFileName), visible)
                .clickOnRenameFileButton(tempFileName)
                .typeInField(fileInPipeline)
                .ok()
                .clearAndFillPipelineFile(fileInPipeline, newFileContent);

        refresh();
        getFirstVersionOfPipeline(pipelineName)
                .clickOnFile(fileInPipeline)
                .shouldContainInCode(newFileContent)
                .close()
                .runPipeline()
                .waitUntilLaunchButtonAppear()
                .launch(this);

        navigationMenu()
                .clusterNodes()
                .waitForTheNode(pipelineName, getLastRunId());
        navigationMenu()
                .runs()
                .activeRuns()
                .validatePipelineIsPresent(pipelineName)
                .stopRun(getLastRunId());
        clusterMenu()
                .removeNode(getLastRunId());
    }

    @Test(priority = 15)
    @TestCase({"EPMCMBIBPC-562"})
    public void checkReadPermissionForBucket() {
        logoutIfNeeded();
        loginAs(admin)
                .library()
                .selectStorage(bucket)
                .createFile(tempFileName)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user, BucketPermission.allow(READ, bucket));
        logout();

        loginAs(user)
                .library()
                .validateStorage(bucket);
    }

    @Test(priority = 16)
    @TestCase({"EPMCMBIBPC-563"})
    public void checkBucketEditProhibitionForReadOnlyUser() {
        logoutIfNeeded();
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .validateElementsAreNotEditable()
                .ensureNotVisible(CREATE_FOLDER, UPLOAD)
                .ensureVisible(EDIT_STORAGE, SELECT_ALL, ADDRESS_BAR, REFRESH, SHOW_METADATA);
    }

    @Test(priority = 17)
    @TestCase({"EPMCMBIBPC-564"})
    public void tryToNavigateToAnotherBucketByNavigationBar() {
        logoutIfNeeded();
        loginAs(user);
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .tryNavigateToAnotherBucket(anotherBucket)
                .messageShouldAppear("You cannot navigate to another storage.");
    }

    @Test(priority = 18)
    @TestCase({"EPMCMBIBPC-565"})
    public void checkWritePermissionForBucket() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(user,
                BucketPermission.allow(WRITE, bucket),
                BucketPermission.allow(READ, bucket));
        logout();
        loginAs(user)
                .library()
                .selectStorage(bucket)
                .validateElementsAreEditable()
                .ensureVisible(CREATE, UPLOAD, EDIT_STORAGE, SELECT_ALL, ADDRESS_BAR, REFRESH, SHOW_METADATA);
    }

    @Test(priority = 19)
    @TestCase({"EPMCMBIBPC-566"})
    public void checkBucketEditingByNonAdminUser() {
        logoutIfNeeded();
        loginAs(admin);

        final DataStoragesTest bucketTests =
                getDataStoragesTest(bucketForDataStoragesTests, presetBucketForDataStoragesTests);

        //EPMCMBIBPC-448
        bucketTests.createDataStorageAndValidate();
        bucketTests.createPresetStorage();

        addNewUserToBucketPermissions(user, bucketForDataStoragesTests);
        givePermissions(user, BucketPermission.allow(WRITE, bucketForDataStoragesTests));
        addNewUserToBucketPermissions(user, presetBucketForDataStoragesTests);
        givePermissions(user, BucketPermission.allow(WRITE, presetBucketForDataStoragesTests));
        logout();

        loginAs(user);
        //EPMCMBIBPC-490
        bucketTests.validateCancelDeletingSeveralFolders();
        //EPMCMBIBPC-454
        bucketTests.createSubfolderInDataStorage();
        //EPMCMBIBPC-455
        bucketTests.navigateToSubfolderAndBackToTheRoot();
        //EPMCMBIBPC-469
        bucketTests.navigateToFolderUsingAddressRow();
        //EPMCMBIBPC-456
        bucketTests.uploadFile();
        //EPMCMBIBPC-457
        bucketTests.downloadFileAndValidate();
        //EPMCMBIBPC-458
        bucketTests.editFolderName();
        //EPMCMBIBPC-468
        bucketTests.editFileName();
        //EPMCMBIBPC-459
        bucketTests.deleteFolder();
        //EPMCMBIBPC-462
        bucketTests.deleteSeveralFiles();
    }

    @Test(priority = 20)
    @TestCase({"EPMCMBIBPC-567"})
    public void checkClusterNodesByNonAdminUser() {
        logoutIfNeeded();
        loginAs(admin);
        addNewUserToToolPermissions(user, registry, group, tool);
        givePermissions(user,
                ToolPermission.allow(READ, tool, registry, group),
                ToolPermission.allow(WRITE, tool, registry, group),
                ToolPermission.allow(EXECUTE, tool, registry, group));
                logout();
        loginAs(user);
        launchToolWithDefaultSettings();
        navigationMenu()
                .clusterNodes()
                .waitForTheNode(Utils.nameWithoutGroup(tool), getLastRunId());
        runsMenu()
                .stopRun(getLastRunId());
        logout();
        loginAs(admin);
        launchToolWithDefaultSettings();
        clusterMenu()
                .waitForTheNode(Utils.nameWithoutGroup(tool), getLastRunId());
        runsMenu()
                .stopRun(getLastRunId());
        sleep(2, SECONDS);
        logout();
        loginAs(user)
                .clusterNodes()
                .assertNodesTableIsEmpty();
    }

    @Test(priority = 22, enabled = false)
    @TestCase({"EPMCMBIBPC-570"})
    public void checkHistoryOfRunsForNonAdminUserWithRunsBefore() {
        logoutIfNeeded();
        loginAs(user);
        navigationMenu()
                .runs()
                .completedRuns()
                .validateRunsListIsNotEmpty()
                .validateOnlyUsersPipelines(user.login);
    }


    @Test(priority = 23, enabled = false)
    @TestCase({"EPMCMBIBPC-571"})
    public void checkToolsPageByUserWithoutPermissionsForDockerRepository() {
        logoutIfNeeded();
        loginAs(admin);
        givePermissions(userWithoutCompletedRuns, RegistryPermission.allow(READ, registry));
        logout();
        loginAs(userWithoutCompletedRuns);
        tools().ensure(byText("No groups configured"));
    }

    @Test(priority = 24)
    @TestCase({"EPMCMBIBPC-572"})
    public void checkToolsPageByReadOnlyUser() {
        try {
            logoutIfNeeded();
            loginAs(admin);
            List<String> adminTools = tools().perform(registry, group, ToolGroup::allToolsNames);
            tools().editRegistry(registry, edition ->
                    edition.permissions()
                            .deleteIfPresent(userWithoutCompletedRuns.login)
                            .deleteIfPresent(userRoleGroup)
                            .closeAll());
            addNewUserToGroupPermissions(userWithoutCompletedRuns, registry, group);
            givePermissions(userWithoutCompletedRuns, GroupPermission.allow(READ, registry, group));
            logout();
            loginAs(userWithoutCompletedRuns);
            List<String> userTools = tools().perform(registry, group, ToolGroup::allToolsNames);
            tools().registry(registry, registry ->
                    registry.ensureGroupAreAvailable(Collections.singletonList(group))
                            .resetMouse()
                            .group(group, ToolGroup::canCreatePersonalGroup));
            assertToolsListsAreEqual(adminTools, userTools);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            tools().editRegistry(registry, edition ->
                    edition.permissions()
                            .addNewGroup(userRoleGroup)
                            .selectByName(userRoleGroup)
                            .showPermissions()
                            .set(READ, ALLOW)
                            .set(WRITE, DENY)
                            .set(EXECUTE, ALLOW)
                            .closeAll());
        }
    }

    @Test(priority = 25)
    @TestCase({"EPMCMBIBPC-573"})
    public void checkToolsPageByExecutePermissionUser() {
        List<String> adminTools = tools().perform(registry, group, ToolGroup::allToolsNames);
        givePermissions(userWithoutCompletedRuns,
                GroupPermission.allow(READ, registry, group),
                GroupPermission.deny(WRITE, registry, group),
                GroupPermission.allow(EXECUTE, registry, group));
        logout();
        loginAs(userWithoutCompletedRuns);
        List<String> userTools = tools().perform(registry, group, ToolGroup::allToolsNames);
        tools().ensureOnlyOneRegistryIsAvailable()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                                .setCommand(defaultCommand)
                                .setDefaultLaunchOptions()
                                .ensureLaunchButtonIsVisible();
        assertToolsListsAreEqual(userTools, adminTools);
    }

    @Test(priority = 26)
    @TestCase({"EPMCMBIBPC-574"})
    public void checkToolsPageByEditPermissionUser() {
        logoutIfNeeded();
        loginAs(admin);
        addNewUserToGroupPermissions(userWithoutCompletedRuns, registry, group);
        givePermissions(userWithoutCompletedRuns, GroupPermission.allow(WRITE, registry, group));
        givePermissions(userWithoutCompletedRuns, GroupPermission.deny(EXECUTE, registry, group));
        logout();
        String command = "echo \"Hi, I'm nginx!\"";
        loginAs(userWithoutCompletedRuns);
        tools()
                .performWithin(registry, group, tool,  tool ->
                        tool
                                .settings()
                                .setDefaultCommand(command)
                                .save()
                );
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool
                                .settings()
                                .ensure(DEFAULT_COMMAND, text(command))
                );
    }

    @Test(priority = 27)
    @TestCase({"EPMCMBIBPC-578"})
    public void checkPipelinePermissionsForGroup() {
        logoutIfNeeded();
        loginAs(admin)
                .library()
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .clickAddNewGroup()
                .typeInField(userGroup)
                .ok()
                .validateGroupHasPermissions(userGroup)
                .validateDeleteButtonIsDisplayedOppositeTo(userGroup)
                .closeAll();
    }

    @Test(priority = 28, enabled = false)
    @TestCase({"EPMCMBIBPC-579"})
    public void setPipelinePermissionsAndCheckItForGroup() {
        logoutIfNeeded();
        loginAs(admin)
                .library()
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewGroup(userGroup)
                .closeAll();
        // Remove user permissions
        givePermissions(user,
                PipelinePermission.inherit(READ, pipelineName),
                PipelinePermission.inherit(WRITE, pipelineName),
                PipelinePermission.inherit(EXECUTE, pipelineName));
        givePermissions(userGroup,
                PipelinePermission.allow(WRITE, pipelineName),
                PipelinePermission.allow(EXECUTE, pipelineName));
        logout();
        loginAs(user)
                .library()
                .clickOnPipeline(pipelineName)
                .assertEditButtonIsDisplayed()
                .assertRunButtonIsDisplayed();
        logout();
        loginAs(userWithoutCompletedRuns)
                .library()
                .clickOnPipeline(pipelineName)
                .assertEditButtonIsDisplayed()
                .assertRunButtonIsDisplayed();
    }

    @Test(priority = 29, enabled = false)
    @TestCase({"EPMCMBIBPC-580"})
    public void setToolPermissionsAndCheckItForGroup() {
        logoutIfNeeded();
        loginAs(admin);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.permissions()
                                .addNewGroup(userGroup)
                                .closeAll()
                );
        givePermissions(userGroup,
                ToolPermission.allow(WRITE, tool, registry, group),
                ToolPermission.allow(EXECUTE, tool, registry, group));
        logout();
        loginAs(user);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.ensure(RUN, visible)
                                .ensure(editButtonFor(FULL_DESCRIPTION), visible)
                                .ensure(editButtonFor(SHORT_DESCRIPTION), visible)

                );
        logout();
        loginAs(userWithoutCompletedRuns);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.ensure(RUN, visible)
                                .ensure(editButtonFor(FULL_DESCRIPTION), visible)
                                .ensure(editButtonFor(SHORT_DESCRIPTION), visible)

                );
    }

    @Test(priority = 30)
    @TestCase({"EPMCMBIBPC-1575"})
    public void validateSearchWithinEditUser() {
        String group = "TESTING_GROUP";
        String part = group.substring(0, group.length() / 2);

        logoutIfNeeded();
        loginAs(admin);

        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .pressCreateGroup()
                .enterGroupName(group)
                .create()
                .ok();

        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(user.login.toUpperCase())
                .edit()
                .searchRoleBySubstring(part)
                .validateRoleAppearedInSearch(group)
                .ok()
                .ok();

        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .searchGroupBySubstring(group)
                .deleteGroup(group)
                .ok();
    }

    @Test(priority = 31)
    @TestCase({"EPMCMBIBPC-1581"})
    public void validateSearchForNoUser() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .clickSearch()
                .pressEnter()
                .ok()
                .ensurePopupIsClosed();

        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .clickSearch()
                .ensure(SEARCH, cssClass("ant-input-affix-wrapper"))
                .ok();
    }

    private PermissionTabAO.UserPermissionsTableAO getUserPipelinePermissions(final Account user,
                                                                              final String pipelineName) {
        return getPipelinePermissionsTab(pipelineName)
                .selectByName(getUserNameByAccountLogin(user.login))
                .showPermissions();
    }

    private PermissionTabAO getPipelinePermissionsTab(final String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab();
    }

    private PermissionTabAO getFolderPermissionsTab(final String folderName) {
        return navigationMenu()
                .library()
                .clickOnFolder(folderName)
                .clickEditButton()
                .clickOnPermissionsTab();
    }

    private void addNewUserToBucketPermissions(final Account user, final String bucket) {
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
    }

    private void addNewUserToRegistryPermissions(final Account account, final String registryName) {
        tools()
                .editRegistry(registryName, settings ->
                        settings.permissions()
                                .addNewUser(account.login)
                                .closeAll()
                );
    }

    private void addNewUserToGroupPermissions(final Account account, final String registryName,
                                              final String groupName) {
        tools()
                .performWithin(registryName, groupName, group ->
                        group.editGroup(settings ->
                                settings.permissions()
                                .addNewUser(account.login)
                                .closeAll())
                );
    }

    private void addNewUserToToolPermissions(final Account account, final String registryName, final String groupName,
                                             final String tool) {
        tools()
                .performWithin(registryName, groupName, tool, t ->
                        t.permissions()
                        .addNewUser(account.login)
                        .closeAll()
                );
    }

    private DataStoragesTest getDataStoragesTest(final String bucket1, final String bucket2) {
        final DataStoragesTest bucketTests = new DataStoragesTest();
        bucketTests.setStorage(bucket1);
        bucketTests.setPresetStorage(bucket2);
        return bucketTests;
    }

    private void launchToolWithDefaultSettings() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .setCommand(defaultCommand)
                .launchTool(this, Utils.nameWithoutGroup(tool));
    }

    private PipelineCodeTabAO getFirstVersionOfPipeline(final String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .sleep(2, SECONDS)
                .firstVersion()
                .codeTab();
    }

    private void assertToolsListsAreEqual(final List<String> userTools, final List<String> adminTools) {
        Collections.sort(userTools);
        Collections.sort(adminTools);

        assertEquals(userTools, adminTools);
    }

    private void discardPermissions() {
        addNewUserToGroupPermissions(userWithoutCompletedRuns, registry, group);
        givePermissions(userWithoutCompletedRuns, RegistryPermission.deny(READ, registry));
        givePermissions(userWithoutCompletedRuns, RegistryPermission.deny(WRITE, registry));
        givePermissions(userWithoutCompletedRuns, RegistryPermission.deny(EXECUTE, registry));

        addNewUserToRegistryPermissions(userWithoutCompletedRuns, registry);
        givePermissions(userWithoutCompletedRuns, GroupPermission.deny(READ, registry, group));
        givePermissions(userWithoutCompletedRuns, GroupPermission.deny(WRITE, registry, group));
        givePermissions(userWithoutCompletedRuns, GroupPermission.deny(EXECUTE, registry, group));

        addNewUserToToolPermissions(user, registry, group, tool);

        givePermissions(user,
                ToolPermission.inherit(READ, tool, registry, group),
                ToolPermission.inherit(WRITE, tool, registry, group),
                ToolPermission.inherit(EXECUTE, tool, registry, group));
    }

    private void createGroupPrerequisites() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToUserManagement()
                        .switchToGroups()
                        .deleteGroupIfPresent(userGroup)
                        .pressCreateGroup()
                        .enterGroupName(userGroup)
                        .sleep(2, SECONDS)
                        .create()
                        .sleep(3, SECONDS)
                        .ok()
        );
        refresh();
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.permissions()
                                .addNewGroup(userGroup)
                                .closeAll()
                );

        addUserToGroup(user.login.toUpperCase(), userGroup);
        addUserToGroup(userWithoutCompletedRuns.login.toUpperCase(), userGroup);

        givePermissions(userGroup,
                ToolPermission.inherit(READ, tool, registry, group),
                ToolPermission.inherit(WRITE, tool, registry, group),
                ToolPermission.inherit(EXECUTE, tool, registry, group));
    }

    private void addUserToGroup(final String userLogin, final String userGroup) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userLogin)
                .edit()
                .addRoleOrGroup(userGroup)
                .sleep(2, SECONDS)
                .ok()
                .sleep(1, SECONDS)
                .closeAll();
    }
}