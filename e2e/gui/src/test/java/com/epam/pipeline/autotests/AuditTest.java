/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import com.epam.pipeline.autotests.ao.AuthenticationPageAO;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_SYSTEM_ACCESS;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.ao.SystemManagementAO.SystemLogsAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.FolderPermission;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.openqa.selenium.By;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.stream.Stream;

public class AuditTest extends AbstractSeveralPipelineRunningTest
        implements Tools, Authorization {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String testFolder = format("auditTestsFolder-%s", Utils.randomSuffix());
    private String storage1 = format("storage1audit%s", Utils.randomSuffix());
    private String storage2 = format("storage2audit%s", Utils.randomSuffix());
    private String storage3 = format("storage3audit%s", Utils.randomSuffix());
    private String storage4 = format("storage4audit%s", Utils.randomSuffix());
    private String storage5 = format("storage5audit%s", Utils.randomSuffix());
    private String storage6 = format("storage6audit%s", Utils.randomSuffix());
    private String storage7 = format("storage7audit%s", Utils.randomSuffix());
    private String folder1 = "folder1";
    private String folder1_new = "folder1_new";
    private String folder2 = "folder2";
    private String file1 = "file1";
    private String file1_new = "file1_new";
    private String file2 = "file2";
    private String file3 = "file3";
    private String inner_file1 = "inner_file1";
    private String inner_file2 = "inner_file2";
    private String inner_file3 = "inner_file3";
    private String inner_file4 = "inner_file4";
    private final File newFile = Utils.createTempFileWithNameAndSize(format("file2%s", Utils.randomSuffix()));
    private String pathStorage1 = "";
    private String pathStorage2 = "";
    private String pathStorage3 = "";
    private String pathStorage4 = "";
    private String pathStorage5 = "";
    private String pathStorage6 = "";
    private String pathStorage7 = "";

    private String runID = "";

    @BeforeClass(alwaysRun = true)
    public void createPreferences() {
        navigationMenu()
                .library()
                .createFolder(testFolder);
        addAccountToFolderPermissions(user, testFolder);
        givePermissions(user,
                FolderPermission.allow(READ, testFolder),
                FolderPermission.allow(WRITE, testFolder),
                FolderPermission.allow(EXECUTE, testFolder)
        );
        library()
                .cd(testFolder)
                .createStorage(storage1);
        pathStorage1 = addStorageContent(storage1);
        pathStorage2 = library()
                .cd(testFolder)
                .createStorage(storage2)
                .selectStorage(storage2)
                .getStoragePath();
        library()
                .cd(testFolder)
                .createStorage(storage4);
        pathStorage4 = addStorageContent(storage4);
        pathStorage5 = library()
                .cd(testFolder)
                .createStorage(storage5)
                .selectStorage(storage5)
                .getStoragePath();
        Stream.of(storage4, storage5)
                .forEach(stor -> library()
                        .selectStorage(stor)
                        .showMetadata()
                        .click(FILE_SYSTEM_ACCESS));
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .selectStorage(storage1)
                .searchStorage(storage2)
                .selectStorage(storage2)
                .searchStorage(storage4)
                .selectStorage(storage4)
                .searchStorage(storage5)
                .selectStorage(storage5)
                .ok()
                .launch(this);
        runID = getLastRunId();
    }

    @AfterClass(alwaysRun = true)
    public void resetPreferences() {
        open(C.ROOT_ADDRESS);
        library()
                .removeNotEmptyFolder(testFolder);
    }

    @BeforeMethod
    void openApplication() {
        open(C.ROOT_ADDRESS);
    }

    @Test
    @TestCase(value = {"3059_1"})
    public void dataAccessAuditOperationsWithFiles() {
        String [] commands = {
                format("pipe storage cp %s/%s %s/", pathStorage1, file1, pathStorage2),
                format("pipe storage mv %s/%s %s/", pathStorage1, file2, pathStorage2),
                format("pipe storage rm %s/%s -y", pathStorage1, file1)
        };
        String [] expected_logs = {
                format("[run_id #%s] READ %s/%s", runID, pathStorage1, file1),
                format("[run_id #%s] WRITE %s/%s", runID, pathStorage2, file1),
                format("[run_id #%s] READ %s/%s", runID, pathStorage1, file2),
                format("[run_id #%s] WRITE %s/%s", runID, pathStorage2, file2),
                format("[run_id #%s] DELETE %s/%s", runID, pathStorage1, file2),
                format("[run_id #%s] DELETE %s/%s", runID, pathStorage1, file1)
        };
        logoutIfNeeded();
        loginAs(user);
        executeCommands(commands);
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    @Test(dependsOnMethods = {"dataAccessAuditOperationsWithFiles"})
    @TestCase(value = {"3059_2"})
    public void dataAccessAuditOperationsWithFolders() {
        String [] commands = {
                format("pipe storage cp -r %s/%s %s/%s", pathStorage1, folder1, pathStorage2, folder1),
                format("pipe storage mv -r %s/%s %s/%s", pathStorage1, folder2, pathStorage2, folder2),
                format("pipe storage rm -r %s/%s -y", pathStorage1, folder1)
        };
        String [] expected_logs = {
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage1, folder1, inner_file1),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage2, folder1, inner_file1),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage1, folder1, inner_file2),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage2, folder1, inner_file2),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage1, folder2, inner_file3),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage2, folder2, inner_file3),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage1, folder2, inner_file3),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage1, folder2, inner_file4),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage2, folder2, inner_file4),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage1, folder2, inner_file4),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage1, folder1, inner_file1),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage1, folder1, inner_file2)
        };
        logoutIfNeeded();
        loginAs(user);
        executeCommands(commands);
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    @Test(dependsOnMethods = {"dataAccessAuditOperationsWithFolders"})
    @TestCase(value = {"3075"})
    public void auditPipeMountOperations() {
        String [] commands = {
                format("echo \"test info\" >> cloud-data/%s/%s", storage2, file3),
                format("cp -rf cloud-data/%s/%s cloud-data/%s/%s", storage2, folder1, storage1, folder1),
                format("mv -f cloud-data/%s/%s cloud-data/%s/%s", storage2, folder2, storage1, folder2),
                format("rm -f cloud-data/%s/%s", storage2, file2)
        };
        String [] expected_logs = {
                format("[run_id #%s] WRITE %s/%s", runID, pathStorage2, file3),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage2, folder1, inner_file1),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage1, folder1, inner_file1),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage2, folder1, inner_file2),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage1, folder1, inner_file2),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage2, folder2, inner_file3),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage1, folder2, inner_file3),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage2, folder2, inner_file3),
                format("[run_id #%s] READ %s/%s/%s", runID, pathStorage2, folder2, inner_file4),
                format("[run_id #%s] WRITE %s/%s/%s", runID, pathStorage1, folder2, inner_file4),
                format("[run_id #%s] DELETE %s/%s/%s", runID, pathStorage2, folder2, inner_file4),
                format("[run_id #%s] DELETE %s/%s", runID, pathStorage2, file2)
        };
        logoutIfNeeded();
        loginAs(user);
        executeCommands(commands);
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    @Test
    @TestCase(value = {"3059_3"})
    public void uiDataAccessAudit() {
        String file2_new = newFile.getName();
        logoutIfNeeded();
        loginAs(admin);
        library()
                .cd(testFolder)
                .createStorage(storage3);
        pathStorage3 = addStorageContent(storage3);
        logoutIfNeeded();
        loginAs(user);
        String [] expected_logs = {
                format("WRITE %s/%s", pathStorage3, file1),
                format("WRITE %s/%s", pathStorage3, file2_new),
                format("READ %s/%s", pathStorage3, file1),
                format("WRITE %s/%s", pathStorage3, file1_new),
                format("DELETE %s/%s", pathStorage3, file1),
                format("READ %s/%s", pathStorage3, file2_new),
                format("WRITE %s/%s", pathStorage3, file2_new),
                format("READ %s/%s/%s", pathStorage3, folder1, inner_file1),
                format("WRITE %s/%s/%s", pathStorage3, folder1_new, inner_file1),
                format("DELETE %s/%s/%s", pathStorage3, folder1, inner_file1),
                format("READ %s/%s/%s", pathStorage3, folder1, inner_file2),
                format("WRITE %s/%s/%s", pathStorage3, folder1_new, inner_file2),
                format("DELETE %s/%s/%s", pathStorage3, folder1, inner_file2),
                format("DELETE %s/%s", pathStorage3, file2_new),
                format("DELETE %s/%s/%s", pathStorage3, folder1_new, inner_file1),
                format("DELETE %s/%s/%s", pathStorage3, folder1_new, inner_file2)
        };
        library()
                .cd(testFolder)
                .selectStorage(storage3)
                .createFileWithContent(file1, "file1 content")
                .uploadFile(newFile)
                .refresh()
                .selectFile(file1)
                .renameTo(file1_new)
                .editFile(file2_new, "new file2 content")
                .refresh()
                .selectFolder(folder1)
                .renameTo(folder1_new)
                .selectFile(file2_new)
                .delete()
                .refresh()
                .selectFolder(folder1_new)
                .delete()
                .selectFile(file1_new)
                .download();
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    @Test
    @TestCase(value = {"3059_4"})
    public void webDavDataAccessAudit() {
        String [] commands = {
                "pipe storage mount -f -m 775 -o allow_other destination",
                format("echo \"test info\" >> destination/%s/%s", storage4, file3),
                format("cp destination/%s/%s destination/%s/%s", storage4, file1, storage5, file1),
                format("cat destination/%s/%s", storage4, file2),
                format("mv destination/%s/%s destination/%s", storage4, file2, storage5),
                format("rm destination/%s/%s -f", storage5, file2),
                format("cp -rf destination/%s/%s destination/%s/%s", storage4, folder1, storage5, folder1),
                format("mv -f destination/%s/%s destination/%s/%s", storage4, folder2, storage5, folder2),
                format("rm -fdr destination/%s/%s", storage4, folder1)
        };
        String [] expected_logs = {
                format("WRITE %s/%s", pathStorage4, file3),
                format("READ %s/%s", pathStorage4, file1),
                format("WRITE %s/%s", pathStorage5, file1),
                format("READ %s/%s", pathStorage4, file2),
                format("MOVE %s/%s %s/%s", pathStorage4, file2, pathStorage5, file2),
                format("DELETE %s/%s", pathStorage5, file2),
                format("READ %s/%s/%s", pathStorage4, folder1, inner_file1),
                format("WRITE %s/%s/%s", pathStorage5, folder1, inner_file1),
                format("READ %s/%s/%s", pathStorage4, folder1, inner_file2),
                format("WRITE %s/%s/%s", pathStorage5, folder1, inner_file2),
                format("MOVE %s/%s %s/%s", pathStorage4, folder2, pathStorage5, folder2),
                format("DELETE %s/%s/%s", pathStorage4, folder1, inner_file1),
                format("DELETE %s/%s/%s", pathStorage4, folder1, inner_file2),
                format("DELETE %s/%s", pathStorage4, folder1)
        };
        logoutIfNeeded();
        loginAs(user);
        executeCommands(commands);
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    @Test
    @TestCase(value = {"3059_5"})
    public void auditOfSharingStorageDataAccess() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .cd(testFolder)
                .createSharingStorage(storage6);
        pathStorage6 = addStorageContent(storage6);
        String sharedStorageLink = library()
                .cd(testFolder)
                .selectStorage(storage6)
                .getSharedStorageLink();
        String [] expected_logs = {
                format("WRITE %s/%s", pathStorage6, file2),
                format("READ %s/%s", pathStorage6, file1),
                format("WRITE %s/%s", pathStorage5, file1),
                format("READ %s/%s", pathStorage4, file2),
                format("MOVE %s/%s %s/%s", pathStorage4, file2, pathStorage5, file2),
                format("DELETE %s/%s", pathStorage5, file2),
                format("READ %s/%s/%s", pathStorage4, folder1, inner_file1),
                format("WRITE %s/%s/%s", pathStorage5, folder1, inner_file1),
                format("READ %s/%s/%s", pathStorage4, folder1, inner_file2),
                format("WRITE %s/%s/%s", pathStorage5, folder1, inner_file2),
                format("MOVE %s/%s %s/%s", pathStorage4, folder2, pathStorage5, folder2),
                format("DELETE %s/%s/%s", pathStorage4, folder1, inner_file1),
                format("DELETE %s/%s/%s", pathStorage4, folder1, inner_file2),
                format("DELETE %s/%s", pathStorage4, folder1)
        };
        sleep(3, SECONDS);
        openSharingStorage(sharedStorageLink);

        new StorageContentAO()
                .uploadFileWithoutValidation(newFile)
                .refresh()
                .selectFile(format("%s (latest)", file1))
                .download();
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
        checkAuditLog(expected_logs);
    }

    private void openSharingStorage(String sharedStorageLink) {
        open(sharedStorageLink);
        if($(By.id("details-button")).exists()) {
            $(By.id("details-button")).click();
            $(By.id("proceed-link")).click();
        }
        new AuthenticationPageAO()
                .login(user.login)
                .password(user.password)
                .signIn();
    }

    private void executeCommands(String [] commands) {
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> {
                    shell.waitUntilTextAppears(getLastRunId());
                    for (String comm : commands) {
                        shell.execute(comm)
                                .waitUntilTextAfterCommandAppears(comm, getLastRunId());
                    }
                    shell.close();
                });
    }

    private void checkAuditLog(String [] logs) {
        SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .setIncludeServiceAccountEventsOption()
                .filterByField("User", user.login)
                .filterByField("Type", "audit");
        for (String mess : logs) {
            systemLogsAO.validateRow(mess, user.login, "audit");
        }
    }

    private String addStorageContent(String storage) {
        return library()
                .selectStorage(storage)
                .createFolder(folder1)
                .createFolder(folder2)
                .createFileWithContent(file1, "file1 content")
                .createFileWithContent(file2, "file2 content")
                .cd(folder1)
                .createFileWithContent(inner_file1, "inner_file1 content")
                .createFileWithContent(inner_file2, "inner_file2 content")
                .cd("..")
                .cd(folder2)
                .createFileWithContent(inner_file3, "inner_file3 content")
                .createFileWithContent(inner_file4, "inner_file4 content")
                .cd("..")
                .getStoragePath();
    }

}
