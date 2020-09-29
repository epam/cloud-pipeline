package com.epam.pipeline.autotests;

import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
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

public class Launch_LimitMountsTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation {
    private String storage1 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storage2 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String command = "sleep 10d";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private String storageID = "";

    @BeforeClass
    public void setPreferences() {
        library()
                .createStorage(storage1)
                .createStorage(storage2)
                .selectStorage(storage2);
        String url = WebDriverRunner.getWebDriver().getCurrentUrl();
        storageID = url.substring(url.lastIndexOf("/") + 1);
    }

    @AfterClass
    public void removeEntities() {
        library()
                .removeStorage(storage1)
                .removeStorage(storage2);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .disableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                                .ensure(SAVE, disabled)
                );
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
                .searchStorage(storage2)
                .selectStorage(storage2)
                .ensureVisible(CLEAR_SELECTION)
                .ensureAll(enabled, OK)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage2));
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2682"})
    public void runPipelineWithLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage2)
                .selectStorage(storage2)
                .ok()
                .launch(this)
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_LIMIT_MOUNTS", storageID), exist)
                .waitForSshLink()
                .waitForTask("MountDataStorages")
                .click(taskWithName("MountDataStorages"))
                .ensure(log(), containsMessages("Found 1 available storage(s). Checking mount options."));
    }
}
