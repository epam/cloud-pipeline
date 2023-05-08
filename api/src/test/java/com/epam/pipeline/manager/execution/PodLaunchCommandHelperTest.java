package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.execution.LaunchCommandTemplateForImagePattern;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PodLaunchCommandHelperTest {

    public static final String COMMAND_FOR_ALL_IMAGES = "command_for_all_images";
    public static final String COMMAND_FOR_CENTOS_7_IMAGE = "command_for_centos_7_image";
    public static final String COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES = "command_for_all_other_centos_images";

    public static final String VALUE = "value";
    public static final String EVALUATED = "evaluated";
    public static final String LAUNCH_COMMAND_TEMPLATE = "command template with: $" + VALUE;
    public static final String EVALUATED_LAUNCH_COMMAND_TEMPLATE = "command template with: " + EVALUATED;

    public static final String LAUNCH_COMMAND_TEMPLATE_WITH_ADDITIONAL_VARS = "command template with: $"
            + VALUE + " and additional $ENV_VAR";
    public static final String EVALUATED_LAUNCH_COMMAND_TEMPLATE_WITH_ADDITIONAL_VARS = "command template with: "
            + EVALUATED + " and additional $ENV_VAR";

    private static final List<LaunchCommandTemplateForImagePattern> COMMAND_TEMPLATES = Arrays.asList(
        LaunchCommandTemplateForImagePattern.builder().image("*").command(COMMAND_FOR_ALL_IMAGES).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos:7").command(COMMAND_FOR_CENTOS_7_IMAGE).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos*")
                .command(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES).build()
    );

    private static final List<LaunchCommandTemplateForImagePattern> COMMAND_TEMPLATES_WRONG_ORDER = Arrays.asList(
        LaunchCommandTemplateForImagePattern.builder().image("*").command(COMMAND_FOR_ALL_IMAGES).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos*")
                .command(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos:7").command(COMMAND_FOR_CENTOS_7_IMAGE).build()
    );

    @Test
    public void shouldPickDefaultLaunchCommandTemplateTest() {
        final String ubuntuLaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/ubuntu:18.04");
        Assert.assertEquals(COMMAND_FOR_ALL_IMAGES, ubuntuLaunchCommand);
    }

    @Test
    public void shouldPickTheMostAccurateLaunchCommandTemplateTest() {
        final String centos7LaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/centos:7");
        Assert.assertEquals(COMMAND_FOR_CENTOS_7_IMAGE, centos7LaunchCommand);
    }

    @Test
    public void shouldPickCentosCommonLaunchCommandTemplateTest() {
        final String centos8LaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/centos:8");
        Assert.assertEquals(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES, centos8LaunchCommand);
    }

    @Test
    public void willPickTheWrongLaunchCommandTemplateBecauseOfWrongOrderOfCommandsInPreferenceTest() {
        final String centos7LaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES_WRONG_ORDER, "registry:5000/library/centos:7");
        Assert.assertNotEquals(COMMAND_FOR_CENTOS_7_IMAGE, centos7LaunchCommand);
        Assert.assertEquals(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES, centos7LaunchCommand);
    }

    @Test
    public void shouldEvaluateLaunchCommandTemplateTest() {
        final String evaluatedCommand = PodLaunchCommandHelper.evaluateLaunchCommandTemplate(
                LAUNCH_COMMAND_TEMPLATE, Collections.singletonMap(VALUE, EVALUATED));
        Assert.assertEquals(EVALUATED_LAUNCH_COMMAND_TEMPLATE, evaluatedCommand);
    }

    @Test
    public void shouldEvaluateOnlySpecificVarsInLaunchCommandTemplateTest() {
        final String evaluatedCommand = PodLaunchCommandHelper.evaluateLaunchCommandTemplate(
                LAUNCH_COMMAND_TEMPLATE_WITH_ADDITIONAL_VARS, Collections.singletonMap(VALUE, EVALUATED));
        Assert.assertEquals(EVALUATED_LAUNCH_COMMAND_TEMPLATE_WITH_ADDITIONAL_VARS, evaluatedCommand);
    }
}