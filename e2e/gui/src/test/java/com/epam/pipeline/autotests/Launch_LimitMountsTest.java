package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;

public class Launch_LimitMountsTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation {
    private String storage1 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storage2 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String command = "sleep 10d";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;

    @AfterTest
    public void removeEntities() {
        library()
                .removeStorage(storage1)
                .removeStorage(storage2);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2681"})
    public void prepareLimitMounts() {
        library()
                .createStorage(storage1)
                .createStorage(storage2);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .disableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                                .ensure(SAVE, disabled)
                )
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL);
    }
}
