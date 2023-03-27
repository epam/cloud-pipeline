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

import static com.codeborne.selenide.Selenide.open;
import com.epam.pipeline.autotests.ao.SystemManagementAO.SystemLogsAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.FolderPermission;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
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
    private String storage1 = format("storage1-3059-%s", Utils.randomSuffix());
    private String storage2 = format("storage2-3059-%s", Utils.randomSuffix());
    private String storage3 = format("storage3-3059-%s", Utils.randomSuffix());
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
    String pathStorage1 = "";
    String pathStorage2 = "";
    String pathStorage3 = "";

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
        Stream.of(storage1, storage2)
                .forEach(stor -> library()
                        .cd(testFolder)
                        .createStorage(stor));
        pathStorage1 = library()
                .selectStorage(storage1)
                .createFileWithContent(file1, "file1 content")
                .createFileWithContent(file2, "file2 content")
                .createFolder(folder1)
                .createFolder(folder2)
                .cd(folder1)
                .createFileWithContent(inner_file1, "inner_file1 content")
                .createFileWithContent(inner_file2, "inner_file2 content")
                .cd("..")
                .cd(folder2)
                .createFile(inner_file3)
                .createFile(inner_file4)
                .cd("..")
                .getStoragePath();
        pathStorage2 = library()
                .selectStorage(storage2)
                .getStoragePath();
    }

    @AfterClass(alwaysRun = true)
    public void resetPreferences() {
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
                format("READ %s/%s", pathStorage1, file1),
                format("WRITE %s/%s", pathStorage2, file1),
                format("READ %s/%s", pathStorage1, file2),
                format("WRITE %s/%s", pathStorage2, file2),
                format("DELETE %s/%s", pathStorage1, file2),
                format("DELETE %s/%s", pathStorage1, file1)
        };
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
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
                format("READ %s/%s/%s", pathStorage1, folder1, inner_file1),
                format("WRITE %s/%s/%s", pathStorage2, folder1, inner_file1),
                format("READ %s/%s/%s", pathStorage1, folder1, inner_file2),
                format("WRITE %s/%s/%s", pathStorage2, folder1, inner_file2),
                format("READ %s/%s/%s", pathStorage1, folder2, inner_file3),
                format("WRITE %s/%s/%s", pathStorage2, folder2, inner_file3),
                format("DELETE %s/%s/%s", pathStorage1, folder2, inner_file3),
                format("READ %s/%s/%s", pathStorage1, folder2, inner_file4),
                format("WRITE %s/%s/%s", pathStorage2, folder2, inner_file4),
                format("DELETE %s/%s/%s", pathStorage1, folder2, inner_file4),
                format("DELETE %s/%s/%s", pathStorage1, folder1, inner_file1),
                format("DELETE %s/%s/%s", pathStorage1, folder1, inner_file2)
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
                format("mv -rf cloud-data/%s/%s cloud-data/%s/%s", storage2, folder2, storage1, folder2),
                format("rm -f cloud-data/%s/%s", storage2, file2)
        };
        String [] expected_logs = {
                format("WRITE %s/%s", pathStorage2, file3),
                format("READ %s/%s/%s", pathStorage2, folder1, inner_file1),
                format("WRITE %s/%s/%s", pathStorage1, folder1, inner_file1),
                format("READ %s/%s/%s", pathStorage2, folder1, inner_file2),
                format("WRITE %s/%s/%s", pathStorage1, folder1, inner_file2),
                format("READ %s/%s/%s", pathStorage2, folder2, inner_file3),
                format("WRITE %s/%s/%s", pathStorage1, folder2, inner_file3),
                format("DELETE %s/%s/%s", pathStorage2, folder2, inner_file3),
                format("READ %s/%s/%s", pathStorage2, folder2, inner_file4),
                format("WRITE %s/%s/%s", pathStorage1, folder2, inner_file4),
                format("DELETE %s/%s/%s", pathStorage2, folder2, inner_file4),
                format("DELETE %s/%s", pathStorage2, file2)
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
        final File newFile = Utils.createTempFile("file2");
        String file2_new = newFile.getName();
        logoutIfNeeded();
        loginAs(user);
        pathStorage3 = library()
                .cd(testFolder)
                .createStorage(storage3)
                .selectStorage(storage3)
                .createFolder(folder1)
                .cd(folder1)
                .createFile(inner_file1)
                .createFile(inner_file2)
                .cd("..")
                .getStoragePath();
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
                .filterByUser(user.login)
                .filterByType("audit");
        for (String mess : logs) {
            systemLogsAO.validateRow(mess, user.login, "audit");
        }
    }

}
