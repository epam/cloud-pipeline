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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_KEY;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE_ICON;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE_ALL_KEYS;

public class ObjectMetadataToolTest extends AbstractBfxPipelineTest implements Navigation {

    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String anotherTool = C.TOOL_WITHOUT_DEFAULT_SETTINGS;
    private final String defaultGroup = C.DEFAULT_GROUP;

    private final String key1 = "1";
    private final String value1 = "2";
    private final String key2 = "2";
    private final String value2 = "3";
    private final String key3 = "qwerty";
    private final String value3 = "asdfg";
    private final String key4 = "very very very long key with spaces";
    private final String value4 = "a";
    private final String key5 = "a";
    private final String value5 = "text with spaces";

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-914"})
    public void addMetadataToToolValidation() {
        tools()
                .performWithin(registry, defaultGroup, tool, tool ->
                        tool.showMetadata(metadata ->
                                metadata.ensure(ADD_KEY, visible)
                                        .addKeyWithValue(key1, value1)
                                        .ensure(REMOVE_ALL_KEYS, visible)
                                        .selectKey(key1)
                                        .ensure(DELETE_ICON, visible)
                                        .validateKeyBackgroundIsGrey()
                        )
                );
    }

    @Test(priority = 1)
    @TestCase(value = {"EPMCMBIBPC-916"})
    public void checkMetadataForTool() {
        tools()
                .performWithin(registry, defaultGroup, anotherTool, anotherTool ->
                        anotherTool.showMetadata(metadata ->
                                metadata.assertKeyNotPresent(key1)
                        )
                );
    }

    @Test(priority = 2)
    @TestCase(value = {"EPMCMBIBPC-915"})
    public void listOfCasesForPipeline() {
        tools()
                .performWithin(registry, defaultGroup, tool, tool ->
                        tool.showMetadata(metadata -> {
                                    //EPMCMBIBPC-842
                                    metadata.selectKeyByOrderNumber(1)
                                            .changeKey(key2)
                                            .assertKeyIs(key2)
                                            .changeValue(value2)
                                            .assertValueIs(value2);
                                    //EPMCMBIBPC-860
                                    metadata.addKeyWithValue(key3, value3)
                                            .addKeyWithValue(key4, value4)
                                            .addKeyWithValue(key5, value5)
                                            .assertKeyWithValueIsPresent(key3, value3)
                                            .assertKeyWithValueIsPresent(key4, value4)
                                            .assertKeyWithValueIsPresent(key5, value5)
                                            //EPMCMBIBPC-863
                                            .deleteKey(key3)
                                            .cancel()
                                            .assertKeyIsPresent(key3)
                                            .deleteKey(key3)
                                            .ensureTitleIs(String.format("Do you want to delete key \"%s\"?", key3))
                                            .ok()
                                            .assertKeyNotPresent(key3)
                                            //EPMCMBIBPC-864
                                            .deleteAllKeys()
                                            .cancel()
                                            .assertNumberOfKeysIs(3)
                                            .deleteAllKeys()
                                            .ensureTitleIs("Do you want to delete all metadata?")
                                            .ok()
                                            .assertNumberOfKeysIs(0);
                                }

                        )
                );
    }
}
