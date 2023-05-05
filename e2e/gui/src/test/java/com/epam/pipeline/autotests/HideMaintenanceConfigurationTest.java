package com.epam.pipeline.autotests;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.Primitive.MAINTENANCE;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.lang.String.format;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class HideMaintenanceConfigurationTest extends AbstractSeveralPipelineRunningTest implements Authorization, Tools {

    private final String MAINTENANCE_TOOL_ENABLED = "ui.run.maintenance.tool.enabled";
    private final String MAINTENANCE_PIPELINE_ENABLED = "ui.run.maintenance.pipeline.enabled";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String[] uiRunMaintenanceToolEnabledInitial;
    private String[] uiRunMaintenancePipelineEnabledInitial;
    private String uiRunMaintenanceEnabled1 = "{\n\"pause\": false\n}";
    private String uiRunMaintenanceEnabled2 = "{\n\"pause\": false,\n\"resume\": false\n}";
    private final String pipeline = format("pipeline3064-%s", Utils.randomSuffix());

    @BeforeClass
    public void getPreferences() {
        uiRunMaintenanceToolEnabledInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(MAINTENANCE_TOOL_ENABLED);
        uiRunMaintenancePipelineEnabledInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(MAINTENANCE_PIPELINE_ENABLED);
    }

    @AfterClass
    public void resetPreferences() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_TOOL_ENABLED, uiRunMaintenanceToolEnabledInitial[0], true)
                .saveIfNeeded();
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_PIPELINE_ENABLED, uiRunMaintenancePipelineEnabledInitial[0], true)
                .saveIfNeeded();
    }

    @Test
    @TestCase(value = {"3064_1"})
    public void optionallyHideMaintenanceConfigurationForToolJobs() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_TOOL_ENABLED, uiRunMaintenanceEnabled1, true)
                .saveIfNeeded();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .setPriceType(ON_DEMAND)
                .ensureVisible(MAINTENANCE)
                .maintenanceConfigure()
                .addRule()
                .typeIsDisables(true)
                .ensure(TYPE, text("Resume"))
                .cancel()
                .launch(this)
                .showLog(getLastRunId())
                .ensureVisible(MAINTENANCE)
                .maintenanceConfigure()
                .addRule()
                .typeIsDisables(true)
                .ensure(TYPE, text("Resume"))
                .cancel();
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_TOOL_ENABLED, uiRunMaintenanceEnabled2, true)
                .saveIfNeeded();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .setPriceType(ON_DEMAND)
                .ensure(MAINTENANCE, not(exist))
                .launch(this)
                .showLog(getLastRunId())
                .ensureNotVisible(MAINTENANCE);
    }

    @Test
    @TestCase(value = {"3064_2"})
    public void optionallyHideMaintenanceConfigurationForPipelineJobs() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_PIPELINE_ENABLED, uiRunMaintenanceEnabled1, true)
                .saveIfNeeded();
        library()
                .createPipeline(pipeline)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .setPriceType(ON_DEMAND)
                .ensureVisible(MAINTENANCE)
                .maintenanceConfigure()
                .addRule()
                .typeIsDisables(true)
                .ensure(TYPE, text("Resume"))
                .cancel()
                .launch(this)
                .showLog(getLastRunId())
                .ensureVisible(MAINTENANCE)
                .maintenanceConfigure()
                .addRule()
                .typeIsDisables(true)
                .ensure(TYPE, text("Resume"))
                .cancel();
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(MAINTENANCE_PIPELINE_ENABLED, uiRunMaintenanceEnabled2, true)
                .saveIfNeeded();
        library()
                .createPipeline(pipeline)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .setPriceType(ON_DEMAND)
                .ensure(MAINTENANCE, not(exist))
                .launch(this)
                .showLog(getLastRunId())
                .ensureNotVisible(MAINTENANCE);
    }
}
