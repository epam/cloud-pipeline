/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.mixins;

import com.epam.pipeline.autotests.ao.ToolGroup;
import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.utils.C;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.openqa.selenium.By;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface Tools extends Navigation {

    String endpoint = C.VALID_ENDPOINT;
    String defaultCommand = "/start.sh";
    String defaultInstanceType = C.DEFAULT_INSTANCE;
    String defaultDiskSize = "20";
    String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;

    default void fallbackToToolDefaultState(final String registryName,
                                            final String groupName,
                                            final String toolName
                                            ) {
        fallbackToToolDefaultState(registryName,
                groupName,
                toolName,
                endpoint,
                defaultCommand,
                defaultInstanceType,
                defaultPriceType,
                defaultDiskSize);
    }

    default void fallbackToToolDefaultState(final String registryName,
                                            final String groupName,
                                            final String toolName,
                                            final String endpoint,
                                            final String command,
                                            final String instance,
                                            final String priceType,
                                            final String diskSize
    ) {
        tools().performWithin(registryName, groupName, toolName, description ->
                description.settings().sleep(1, SECONDS)
                        .also(removeAllEndpoints())
                        .also(addEndpoint(endpoint))
                        .performIf(DEFAULT_COMMAND, not(text(command)), setDefaultCommand(command))
                        .performIf(INSTANCE_TYPE, not(text(instance)), setInstanceType(instance))
                        .performIf(PRICE_TYPE, not(text(priceType)), setPriceType(priceType))
                        .performIf(DISK, not(text(diskSize)), setDisk(diskSize))
                        .save()
        );
    }

    default Consumer<ToolGroup> deleteTool(final String toolName) {
        return group -> group.tool(toolName, tool ->
                tool.sleep(1, SECONDS)
                        .delete().ensureTitleIs("Are you sure you want to delete tool?").ok()
        );
    }

    default Consumer<ToolSettings> removeAllEndpoints() {
        return settings -> settings
                .performWhile(PORT, contains(button("Delete")),
                        click(button("Delete")).andThen(sleepFor(1, SECONDS))
                )
                .resetMouse();
    }

    default Consumer<ToolSettings> sleepFor(final int times, final TimeUnit units) {
        return settings -> settings.sleep(times, units);
    }

    default Consumer<ToolSettings> click(final By qualifier) {
        return settings -> settings.click(qualifier);
    }

    default Consumer<ToolSettings> addEndpoint(final String endpoint) {
        return settings -> settings.addEndpoint(endpoint).save();
    }

    default <TAB extends ToolTab<TAB>> Consumer<TAB> setDefaultCommand(final String command) {
        return tool -> tool.settings().setDefaultCommand(command).save();
    }
    default <TAB extends ToolTab<TAB>> Consumer<TAB> setInstanceType(final String instance) {
        return tool -> tool.settings().setInstanceType(instance).save();
    }
    default <TAB extends ToolTab<TAB>> Consumer<TAB> setPriceType(final String priceType) {
        return tool -> tool.settings().setPriceType(priceType).save();
    }
    default <TAB extends ToolTab<TAB>> Consumer<TAB> setDisk(final String diskSize) {
        return tool -> tool.settings().setDisk(diskSize).save();
    }

}
