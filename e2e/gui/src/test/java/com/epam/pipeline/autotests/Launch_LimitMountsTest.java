package com.epam.pipeline.autotests;

import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH_INPUT;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;
import static java.lang.String.format;

public class Launch_LimitMountsTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation {
    private String storage1 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storage2 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String mountDataStoragesTask = "MountDataStorages";
    private String storageID = "";
    private String testRunID = "";

    @BeforeClass
    public void setPreferences() {
        library()
                .createStorage(storage1)
                .createStorage(storage2)
                .selectStorage(storage1)
                .validateHeader(storage1);

        String url = WebDriverRunner.getWebDriver().getCurrentUrl();
        storageID = url.substring(url.lastIndexOf("/") + 1);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .disableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                                .ensure(SAVE, disabled)
                );
    }

    @AfterClass
    public void removeEntities() {
        library()
                .removeStorage(storage1)
                .removeStorage(storage2);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2681"})
    public void prepareLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .selectDataStoragesToLimitMounts()
                .ensureVisible(SEARCH_INPUT)
                .ensureAll(disabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE)
                .ensureAll(enabled, CLEAR_SELECTION, CANCEL, OK)
                .validateFields("", "Name", "Type")
                .storagesCountShouldBeGreaterThan(2)
                .clearSelection()
                .ensureAll(enabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE)
                .ensureAll(disabled, OK)
                .ensureNotVisible(CLEAR_SELECTION)
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ensureVisible(CLEAR_SELECTION)
                .ensureAll(enabled, OK)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage1));
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2682"})
    public void runPipelineWithLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ok()
                .launch(this)
                .showLog(testRunID = getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_LIMIT_MOUNTS", storage1), exist)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .click(taskWithName(mountDataStoragesTask))
                .ensure(log(), containsMessages("Found 1 available storage(s). Checking mount options."))
                .ensure(log(), containsMessages(format("Run is launched with mount limits (%s) Only 1 storages will be mounted", storageID)))
                .ensure(log(), containsMessages(format("-->%s mounted to /cloud-data/%s", storage1.toLowerCase(), storage1.toLowerCase())))
                .ssh(shell -> shell
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage1.toLowerCase())
                        .assertPageDoesNotContain(storage2.toLowerCase())
                        .close());
    }

    @Test(dependsOnMethods = {"runPipelineWithLimitMounts"})
    @TestCase(value = {"EPMCMBIBPC-2683"})
    public void rerunPipelineWithoutLimitMounts() {
        runsMenu()
                .showLog(testRunID)
                .stop(format("pipeline-%s", testRunID))
                .clickOnRerunButton()
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text(storage1))
                .selectDataStoragesToLimitMounts()
                .selectAllNonSensitive()
                .ok()
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .launch(this)
                .showLog(getRunId())
                .ensureNotVisible(PARAMETERS)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .clickMountBuckets()
                .logContainsMessage(" available storage(s). Checking mount options.")
                .checkAvailableStoragesCount(2)
                .ensure(log(), not(containsMessages("Run is launched with mount limits")))
                .logContainsMessage(format("-->%s mounted to /cloud-data/%s", storage1.toLowerCase(), storage1.toLowerCase()))
                .logContainsMessage(format("-->%s mounted to /cloud-data/%s", storage2.toLowerCase(), storage2.toLowerCase()))
                .ssh(shell -> shell
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage1.toLowerCase())
                        .assertOutputContains(storage2.toLowerCase())
                        .close());
    }
}
