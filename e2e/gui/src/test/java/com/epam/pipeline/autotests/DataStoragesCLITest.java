package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static java.lang.String.format;

@Listeners(value = ConditionalTestAnalyzer.class)
public class DataStoragesCLITest extends AbstractSinglePipelineRunningTest
        implements Authorization, Navigation {

    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private String storage3 = "dataStorageCLI-" + Utils.randomSuffix();
    private String folder1 = "3-folderDataStorageCLI-" + Utils.randomSuffix();
    private String folder2 = "2-folderDataStorageCLI-" + Utils.randomSuffix();
    private String folder3 = "4-folderDataStorageCLI-" + Utils.randomSuffix();
    private String folder4 = "5-folderDataStorageCLI-" + Utils.randomSuffix();
    private String fileFor1339_1 = "6-fileFor1339-" + Utils.randomSuffix();
    private String fileFor1339_2 = "1-fileFor1339-" + Utils.randomSuffix();
    private String fileFor1339_3 = "fileFor1339-" + Utils.randomSuffix();
    private String pathStorage3 = "";

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        Utils.removeStorages(this, storage3);
    }

    @Test
    @TestCase(value = {"1339_1"})
    public void checkPipeStorageLsPaging() {
        String[] commands =
                {format("pipe storage ls %s/", pathStorage3),
                 format("pipe storage ls --page 2 %s/", pathStorage3),
                 format("pipe storage ls -p 4 %s/", pathStorage3)};
        pathStorage3 = library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage3)
                .setEnableVersioning(true)
                .ok()
                .selectStorage(storage3)
                .createFolder(folder1)
                .createFolder(folder2)
                .createFolder(folder3)
                .createFolder(folder4)
                .createFile(fileFor1339_1)
                .createFile(fileFor1339_2)
                .getStoragePath();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(commands[0])
                        .assertPageAfterCommandContainsStrings(commands[0],
                                folder1, folder2, folder3, folder4, fileFor1339_1, fileFor1339_2)
                        .assertResultsCount(commands[0], getRunId(), 6)
                        .execute(commands[1])
                        .assertPageAfterCommandContainsStrings(commands[1], folder2, fileFor1339_2)
                        .assertPageAfterCommandNotContainsStrings(commands[1],
                                folder1, folder3, folder4, fileFor1339_1)
                        .assertResultsCount(commands[1], getRunId(), 2)
                        .execute(commands[2])
                        .assertPageAfterCommandContainsStrings(commands[2],
                                folder1, folder2, folder3, fileFor1339_2)
                        .assertPageAfterCommandNotContainsStrings(commands[2], folder4, fileFor1339_1)
                        .assertResultsCount(commands[2], getRunId(), 4)
                        .close());
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"checkPipeStorageLsPaging"})
    @TestCase(value = {"1339_2"})
    public void checkPipeStorageLsPagingOfVersions() {
        library()
                .selectStorage(storage3)
                .createAndEditFile(fileFor1339_3, "initial description")
                .editFile(fileFor1339_3, "1st update")
                .editFile(fileFor1339_3, "2nd update")
                .editFile(fileFor1339_3, "3th update")
                .editFile(fileFor1339_3, "4th update");
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(format("pipe storage ls -l -v %s/%s", pathStorage3, fileFor1339_3))
                        .assertOutputContains(folder1, folder2, folder3, folder4, fileFor1339_1, fileFor1339_2)
                        .execute(format("pipe storage ls -l -v --page 3 %s/%s", pathStorage3, fileFor1339_3))
                        .assertOutputContains(folder1, folder2, fileFor1339_1)
                        .execute(format("pipe storage ls -l -v -p 1 %s/%s", pathStorage3, fileFor1339_3))
                        .assertOutputContains(folder1, folder2, folder3, folder4, fileFor1339_1, fileFor1339_2)
                        .close());
    }
}
