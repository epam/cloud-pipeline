/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.cluster.LaunchCapability;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class PipelineConfigurationLaunchCapabilitiesProcessorTest {

    private static final String INTEGER_TYPE = PipeConfValueVO.INTEGER_TYPE;
    private static final String STRING_TYPE = PipeConfValueVO.STRING_TYPE;
    private static final String BOOLEAN_TYPE = PipeConfValueVO.BOOLEAN_TYPE;

    private static final Map<String, PipeConfValueVO> INITIAL_PARAMS = mapOf(
            Pair.of("initial_string_param", new PipeConfValueVO("value", STRING_TYPE)),
            Pair.of("initial_boolean_param", new PipeConfValueVO("true", BOOLEAN_TYPE))
    );

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final PipelineConfigurationLaunchCapabilitiesProcessor launchCapabilitiesProcessor =
            new PipelineConfigurationLaunchCapabilitiesProcessor(preferenceManager);

    @Test
    public void processShouldReturnOriginalParamsIfThereAreNoCapabilities() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, null);

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(INITIAL_PARAMS);

        assertEquals(actual, INITIAL_PARAMS);
    }

    @Test
    public void processShouldReturnOriginalParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_first_param", "first_value"),
                                Pair.of("capability_second_param", "second_value")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(INITIAL_PARAMS);

        assertEquals(actual, INITIAL_PARAMS);
    }

    @Test
    public void processShouldReturnCapabilityParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_first_param", "first_value"),
                                Pair.of("capability_second_param", "second_value")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("capability_first_param", new PipeConfValueVO("first_value", STRING_TYPE)),
                Pair.of("capability_second_param", new PipeConfValueVO("second_value", STRING_TYPE))
        ));
    }

    @Test
    public void processShouldReturnCapabilityParamsIgnoringCase() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("CAPABILITY_FIRST", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_first_param", "first_value")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_second_param", "second_value")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_CAPABILITY_SECOND", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_CAPABILITY_SECOND", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("capability_first_param", new PipeConfValueVO("first_value", STRING_TYPE)),
                Pair.of("capability_second_param", new PipeConfValueVO("second_value", STRING_TYPE))
        ));
    }

    @Test
    public void processShouldNotReturnParentCapabilityParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_parent", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_parent_param", "parent_value")
                        ))
                        .capabilities(mapOf(
                                Pair.of("capability_child", LaunchCapability.builder()
                                        .params(mapOf(
                                                Pair.of("capability_child_param", "child_value")
                                        ))
                                        .build())
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_parent", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_parent", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));
    }

    @Test
    public void processShouldReturnChildCapabilityParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_parent", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_parent_param", "parent_value")
                        ))
                        .capabilities(mapOf(
                                Pair.of("capability_child", LaunchCapability.builder()
                                        .params(mapOf(
                                                Pair.of("capability_child_param", "child_value")
                                        ))
                                        .build())
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_child", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_child", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("capability_child_param", new PipeConfValueVO("child_value", STRING_TYPE))
        ));
    }

    @Test
    public void processShouldReturnMultipleChildCapabilityParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_parent", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_parent_param", "parent_value")
                        ))
                        .capabilities(mapOf(
                                Pair.of("capability_child_first", LaunchCapability.builder()
                                        .params(mapOf(
                                                Pair.of("capability_child_first_param", "first_value")
                                        ))
                                        .build()),
                                Pair.of("capability_child_second", LaunchCapability.builder()
                                        .params(mapOf(
                                                Pair.of("capability_child_second_param", "second_value")
                                        ))
                                        .build())
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_child_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_child_second", new PipeConfValueVO("true", BOOLEAN_TYPE))
        ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_child_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_child_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("capability_child_first_param", new PipeConfValueVO("first_value", STRING_TYPE)),
                Pair.of("capability_child_second_param", new PipeConfValueVO("second_value", STRING_TYPE))
        ));
    }

    @Test
    public void processShouldReturnOriginalParamsAndCapabilityParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("capability_first_param", "first_value"),
                                Pair.of("capability_second_param", "second_value")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("capability_first_param", new PipeConfValueVO("first_value", STRING_TYPE)),
                Pair.of("capability_second_param", new PipeConfValueVO("second_value", STRING_TYPE))
        )));
    }

    @Test
    public void processShouldOverrideOriginalIntegerParamsWithCapabilityIntegerParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("integer_param", "2")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("integer_param", new PipeConfValueVO("1", INTEGER_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("integer_param", new PipeConfValueVO("2", INTEGER_TYPE))
        )));
    }

    @Test
    public void processShouldOverrideOriginalIntegerParamsWithMultipleCapabilityIntegerParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_first", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("integer_param", "2")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("integer_param", "3")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("integer_param", new PipeConfValueVO("1", INTEGER_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("integer_param", new PipeConfValueVO("3", INTEGER_TYPE))
        )));
    }

    @Test
    public void processShouldOverrideOriginalBooleanParamsWithCapabilityBooleanParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("boolean_param", "true")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("boolean_param", new PipeConfValueVO("false", BOOLEAN_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("boolean_param", new PipeConfValueVO("true", BOOLEAN_TYPE))
        )));
    }

    @Test
    public void processShouldOverrideOriginalBooleanParamsWithMultipleCapabilityBooleanParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_first", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("boolean_param", "true")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("boolean_param", "false")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("boolean_param", new PipeConfValueVO("true", BOOLEAN_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("boolean_param", new PipeConfValueVO("false", BOOLEAN_TYPE))
        )));
    }

    @Test
    public void processShouldOverrideMultipleCapabilityImplicitBooleanParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_first", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("boolean_param", "true")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("boolean_param", "false")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE))
                ));

        assertEquals(actual, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("boolean_param", new PipeConfValueVO("false", BOOLEAN_TYPE))
        ));
    }

    @Test
    public void processShouldMergeOriginalStringParamsWithCapabilityStringParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "2")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("string_param", new PipeConfValueVO("1", STRING_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("string_param", new PipeConfValueVO("1,2", STRING_TYPE))
        )));
    }

    @Test
    public void processShouldMergeOriginalStringParamsWithMultipleCapabilityStringParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_first", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "2")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "3")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("string_param", new PipeConfValueVO("1", STRING_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("string_param", new PipeConfValueVO("1,2,3", STRING_TYPE))
        )));
    }

    @Test
    public void processShouldSplitMergeOriginalStringParamsWithCapabilityStringParamsIfThereIsEnabledCapability() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "2,3")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("string_param", new PipeConfValueVO("1,2", STRING_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("string_param", new PipeConfValueVO("1,2,3", STRING_TYPE))
        )));
    }

    @Test
    public void processShouldSplitMergeOriginalStringParamsWithMultipleCapabilityStringParams() {
        set(SystemPreferences.LAUNCH_CAPABILITIES, mapOf(
                Pair.of("capability_first", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "2,3")
                        ))
                        .build()),
                Pair.of("capability_second", LaunchCapability.builder()
                        .params(mapOf(
                                Pair.of("string_param", "3,4")
                        ))
                        .build())
        ));

        final Map<String, PipeConfValueVO> actual = launchCapabilitiesProcessor.process(CommonUtils.mergeMaps(
                INITIAL_PARAMS, mapOf(
                        Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                        Pair.of("string_param", new PipeConfValueVO("1,2", STRING_TYPE))
                )));

        assertEquals(actual, CommonUtils.mergeMaps(INITIAL_PARAMS, mapOf(
                Pair.of("CP_CAP_CUSTOM_capability_first", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("CP_CAP_CUSTOM_capability_second", new PipeConfValueVO("true", BOOLEAN_TYPE)),
                Pair.of("string_param", new PipeConfValueVO("1,2,3,4", STRING_TYPE))
        )));
    }

    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(final Pair<K, V>... items) {
        return Arrays.stream(items)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private <T> void set(final AbstractSystemPreference<T> preference, final T value) {
        doReturn(value).when(preferenceManager).getPreference(preference);
    }

    private void assertEquals(final Map<String, PipeConfValueVO> actual, final Map<String, PipeConfValueVO> expected) {
        expected.forEach((key, value) -> assertEquals(actual.get(key), value));
    }

    private void assertEquals(final PipeConfValueVO actual, final PipeConfValueVO expected) {
        assertNotNull(actual);
        assertThat(actual.getType(), is(expected.getType()));
        assertThat(actual.getValue(), is(expected.getValue()));
    }
}
