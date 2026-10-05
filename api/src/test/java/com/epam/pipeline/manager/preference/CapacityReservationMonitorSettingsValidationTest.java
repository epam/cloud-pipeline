/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.preference;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.config.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Without a finalizing lead time the monitor cannot tell when to warn that a reservation is ending, and used to throw
 * on every cycle instead - so the preference refuses to be saved without one.
 *
 * <p>A Spring test only because the validator parses through {@link JsonMapper}, whose mapper the context sets up.
 */
public class CapacityReservationMonitorSettingsValidationTest extends AbstractSpringTest {

    @Test
    public void shouldAcceptSettingsWithALeadTime() {
        assertThat(validate("{\"finalizingLeadHours\": 48}")).isTrue();
        assertThat(validate("{\"finalizingLeadHours\": 0}")).isTrue();
    }

    @Test
    public void shouldAcceptTheDefaultItShipsWith() {
        final String defaultValue = JsonMapper.convertDataToJsonStringForQuery(
                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_MONITOR_SETTINGS.getDefaultValue());

        assertThat(validate(defaultValue)).isTrue();
    }

    @Test
    public void shouldRefuseSettingsWithoutALeadTime() {
        assertThatThrownBy(() -> validate("{}")).hasMessageContaining("finalizingLeadHours");
        assertThatThrownBy(() -> validate("{\"finalizingLeadHours\": null}"))
                .hasMessageContaining("finalizingLeadHours");
    }

    @Test
    public void shouldRefuseMissingSettings() {
        assertThatThrownBy(() -> validate(null)).hasMessageContaining("required");
        assertThatThrownBy(() -> validate("")).hasMessageContaining("required");
    }

    @Test
    public void shouldRefuseANegativeLeadTime() {
        assertThatThrownBy(() -> validate("{\"finalizingLeadHours\": -1}")).hasMessageContaining("negative");
    }

    @Test
    public void shouldRefuseSomethingThatIsNotTheSettings() {
        assertThat(validate("not json")).isFalse();
    }

    private static boolean validate(final String value) {
        return PreferenceValidators.isValidCapacityReservationMonitorSettings.test(value, Collections.emptyMap());
    }
}
