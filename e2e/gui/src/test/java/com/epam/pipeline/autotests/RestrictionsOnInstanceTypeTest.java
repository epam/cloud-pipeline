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

import static com.epam.pipeline.autotests.ao.Primitive.CODE_TAB;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;

@Listeners(value = ConditionalTestAnalyzer.class)
public class RestrictionsOnInstanceTypeTest extends AbstractBfxPipelineTest
        implements Navigation, Authorization {

    // The drop-down lists of instance types show only the types that cluster.allowed.instance.types (and
    // cluster.allowed.instance.types.docker for a tool) allow: the family of the default instance type of the tests
    // is shown, and a family that neither preference allows is not
    private static final List<String> CANDIDATE_FAMILIES =
            Arrays.asList("m6i", "c6i", "r6i", "m5", "c5", "r4", "t3", "t2", "m4", "c4");
    private final String instanceFamilyName = C.DEFAULT_INSTANCE_FAMILY_NAME;
    private String instanceFamilyNameOld;
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String folder = "restrictionTestFolder-" + Utils.randomSuffix();
    private final String pipeline = "restrictionTestPipeline" + Utils.randomSuffix();
    private final String configuration = "restrictionTestConfiguration" + Utils.randomSuffix();

    @AfterClass
    public void removeEntities() {
        library()
                .refresh()
                .removeNotEmptyFolder(folder);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2642"})
    public void validationOfInstanceTypesRestrictionsSystemSettings() {
       loginAs(admin);
       instanceFamilyNameOld = notAllowedFamily();
       library()
               .createFolder(folder)
               .cd(folder)
               .createPipeline(pipeline)
               .clickOnDraftVersion(pipeline)
               .configurationTab()
               .editConfiguration("default", profile ->
                       profile
                               .expandTab(EXEC_ENVIRONMENT)
                               .sleep(2, SECONDS)
                               .checkDropDownOptions(INSTANCE_TYPE, instanceFamilyName, instanceFamilyNameOld))
               .click(CODE_TAB)
               .refresh();
             //  .exitFromConfigurationWithoutSaved();
       library()
               .cd(folder)
               .createConfiguration(configuration)
               .configurationWithin(configuration, configuration ->
                       configuration
                               .expandTabs(execEnvironmentTab)
                               .checkDropDownOptions(INSTANCE_TYPE, instanceFamilyName, instanceFamilyNameOld));
       tools()
               .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
               .expandTab(EXEC_ENVIRONMENT)
               .checkDropDownOptions(INSTANCE_TYPE, instanceFamilyName, instanceFamilyNameOld);
       }

    private static String notAllowedFamily() {
        final List<Pattern> allowed = Stream.of("cluster.allowed.instance.types", "cluster.allowed.instance.types.docker")
                .map(name -> PlatformApi.get("preferences/" + name).path("value").asText(""))
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(mask -> !mask.isEmpty())
                .map(mask -> Pattern.compile(Pattern.quote(mask).replace("*", "\\E.*\\Q")))
                .collect(Collectors.toList());
        return CANDIDATE_FAMILIES.stream()
                .filter(family -> allowed.stream().noneMatch(mask -> mask.matcher(family + ".xlarge").matches()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The allowed instance types allow all of "
                        + CANDIDATE_FAMILIES + ": no family to check the restriction with"));
    }
}
