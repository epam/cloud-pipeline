package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.execution.LaunchCommandTemplateForImagePattern;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PodLaunchCommandHelperTest {

    public static final String COMMAND_FOR_ALL_IMAGES = "command_for_all_images";
    public static final String COMMAND_FOR_CENTOS_7_IMAGE = "command_for_centos_7_image";
    public static final String COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES = "command_for_all_other_centos_images";
    private static final List<LaunchCommandTemplateForImagePattern> COMMAND_TEMPLATES = Arrays.asList(
        LaunchCommandTemplateForImagePattern.builder().image("*").command(COMMAND_FOR_ALL_IMAGES).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos:7").command(COMMAND_FOR_CENTOS_7_IMAGE).build(),
        LaunchCommandTemplateForImagePattern.builder().image("centos*")
                .command(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES).build()
    );

    @Test
    public void pickLaunchCommandTemplateTest() {
        final String ubuntuLaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/ubuntu:18.04");
        Assert.assertEquals(COMMAND_FOR_ALL_IMAGES, ubuntuLaunchCommand);

        final String centos8LaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/centos:8");
        Assert.assertEquals(COMMAND_FOR_ALL_OTHER_CENTOS_IMAGES, centos8LaunchCommand);

        final String centos7LaunchCommand = PodLaunchCommandHelper
                .pickLaunchCommandTemplate(COMMAND_TEMPLATES, "registry:5000/library/centos:7");
        Assert.assertEquals(COMMAND_FOR_CENTOS_7_IMAGE, centos7LaunchCommand);
    }

    @Test
    public void evaluateLaunchCommandTemplate() {
    }
}