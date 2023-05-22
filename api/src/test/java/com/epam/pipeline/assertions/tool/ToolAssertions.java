/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.assertions.tool;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;

import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public final class ToolAssertions {

    private ToolAssertions() {
        // no-op
    }

    public static void assertRegistryTools(final List<DockerRegistry> actualRegistries,
                                           final List<DockerRegistry> expectedRegistries) {
        actualRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        expectedRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        assertThat(actualRegistries.size(), is(expectedRegistries.size()));
        assertThat(actualRegistries.size(), greaterThan(0));
        for (int i = 0; i < actualRegistries.size(); i++) {
            final DockerRegistry dockerRegistry = actualRegistries.get(i);
            final DockerRegistry expectedRegistry = expectedRegistries.get(i);
            final List<Tool> actualTools = dockerRegistry.getTools();
            final List<Tool> expectedTools = expectedRegistry.getTools();
            assertTools(actualTools, expectedTools);
        }
    }

    public static void assertRegistryGroups(final List<DockerRegistry> actualRegistries,
                                            final List<DockerRegistry> expectedRegistries) {
        actualRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        expectedRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        assertThat(actualRegistries.size(), is(expectedRegistries.size()));
        assertThat(actualRegistries.size(), greaterThan(0));
        for (int i = 0; i < actualRegistries.size(); i++) {
            final DockerRegistry actualRegistry = actualRegistries.get(i);
            final DockerRegistry expectedRegistry = expectedRegistries.get(i);
            final List<ToolGroup> actualGroups = actualRegistry.getGroups();
            final List<ToolGroup> expectedGroups = expectedRegistry.getGroups();
            assertGroups(actualGroups, expectedGroups);
        }
    }

    public static void assertGroups(final List<ToolGroup> actualGroups, final List<ToolGroup> expectedGroups) {
        actualGroups.sort(Comparator.comparing(ToolGroup::getId));
        expectedGroups.sort(Comparator.comparing(ToolGroup::getId));
        assertThat(actualGroups.size(), is(expectedGroups.size()));
        assertThat(actualGroups.size(), greaterThan(0));
        for (int i = 0; i < actualGroups.size(); i++) {
            final ToolGroup actualGroup = actualGroups.get(i);
            final ToolGroup expectedGroup = expectedGroups.get(i);
            final List<Tool> actualTools = actualGroup.getTools();
            final List<Tool> expectedTools = expectedGroup.getTools();
            assertTools(actualTools, expectedTools);
        }
    }

    public static void assertTools(final List<Tool> actualTools, final List<Tool> expectedTools) {
        actualTools.sort(Comparator.comparing(Tool::getId));
        expectedTools.sort(Comparator.comparing(Tool::getId));
        assertThat(actualTools.size(), is(expectedTools.size()));
        assertThat(actualTools.size(), greaterThan(0));
        for (int i = 0; i < actualTools.size(); i++) {
            final Tool actualTool = actualTools.get(i);
            final Tool expectedTool = expectedTools.get(i);
            assertTools(actualTool, expectedTool);
        }
    }

    public static void assertTools(final Tool actualTool, final Tool expectedTool) {
        assertThat(actualTool.getId(), is(expectedTool.getId()));
        assertThat(actualTool.getImage(), is(expectedTool.getImage()));
        assertThat(actualTool.getLink(), is(expectedTool.getLink()));
        assertThat(actualTool.getCpu(), is(expectedTool.getCpu()));
        assertThat(actualTool.getRam(), is(expectedTool.getRam()));
        assertThat(actualTool.getDefaultCommand(), is(expectedTool.getDefaultCommand()));
        assertThat(actualTool.getLabels(), is(expectedTool.getLabels()));
        assertThat(actualTool.getEndpoints(), is(expectedTool.getEndpoints()));
        assertThat(actualTool.getShortDescription(), is(expectedTool.getShortDescription()));
        assertThat(actualTool.getIconId(), is(expectedTool.getIconId()));
        assertThat(actualTool.isGpuEnabled(), is(expectedTool.isGpuEnabled()));
    }
}
