/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.entity.preference.Preference;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterKeepAlivePreferencesValidationTest {

    private static final String MINUTES_TILL_HOUR = ClusterKeepAlivePolicy.MINUTES_TILL_HOUR.name();
    private static final String IDLE_MINUTES = ClusterKeepAlivePolicy.IDLE_MINUTES.name();
    private static final String MINUTES_KEY = SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES.getKey();
    private static final String POLICY_KEY = SystemPreferences.CLUSTER_KEEP_ALIVE_POLICY.getKey();

    @Test
    public void keepAliveMinutesShouldBeLessThanAnHourUnderMinutesTillHour() {
        assertTrue(isValidMinutes("1", MINUTES_TILL_HOUR));
        assertTrue(isValidMinutes("59", MINUTES_TILL_HOUR));
        assertThrows(IllegalArgumentException.class, () -> isValidMinutes("60", MINUTES_TILL_HOUR));
        assertThrows(IllegalArgumentException.class, () -> isValidMinutes("1000", MINUTES_TILL_HOUR));
    }

    @Test
    public void keepAliveMinutesShouldAllowAnyDurationUnderIdleMinutes() {
        assertTrue(isValidMinutes("60", IDLE_MINUTES));
        assertTrue(isValidMinutes("1000", IDLE_MINUTES));
    }

    @Test
    public void keepAliveErrorShouldNameTheLimit() {
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> isValidMinutes("1000", MINUTES_TILL_HOUR));
        assertTrue(error.getMessage().contains("from 1 to 59"));
    }

    @Test
    public void keepAliveMinutesShouldStayGreaterThanZero() {
        assertFalse(isValidMinutes("0", IDLE_MINUTES));
        assertFalse(isValidMinutes("0", MINUTES_TILL_HOUR));
    }

    @Test
    public void keepAlivePolicyShouldNotSwitchToMinutesTillHourWithAnHourOrMore() {
        assertTrue(isValidPolicy(MINUTES_TILL_HOUR, "10"));
        assertThrows(IllegalArgumentException.class, () -> isValidPolicy(MINUTES_TILL_HOUR, "60"));
        assertThrows(IllegalArgumentException.class, () -> isValidPolicy(MINUTES_TILL_HOUR, "1000"));
        assertTrue(isValidPolicy(IDLE_MINUTES, "1000"));
    }

    @Test
    public void keepAlivePolicyShouldStayAKnownPolicy() {
        assertFalse(isValidPolicy("UNKNOWN", "10"));
    }

    @Test
    public void keepAlivePreferencesShouldDependOnEachOther() {
        assertTrue(SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES.getDependencies().containsKey(POLICY_KEY));
        assertTrue(SystemPreferences.CLUSTER_KEEP_ALIVE_POLICY.getDependencies().containsKey(MINUTES_KEY));
    }

    private boolean isValidMinutes(final String minutes, final String policy) {
        return SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES.validate(minutes, keepAlivePreferences(minutes, policy));
    }

    private boolean isValidPolicy(final String policy, final String minutes) {
        return SystemPreferences.CLUSTER_KEEP_ALIVE_POLICY.validate(policy, keepAlivePreferences(minutes, policy));
    }

    /**
     * The preferences a validator gets, as {@code SystemPreferences.validate} passes them: the dependencies and the
     * validated preference itself.
     */
    private Map<String, Preference> keepAlivePreferences(final String minutes, final String policy) {
        final Map<String, Preference> preferences = new HashMap<>();
        preferences.put(MINUTES_KEY, new Preference(MINUTES_KEY, minutes));
        preferences.put(POLICY_KEY, new Preference(POLICY_KEY, policy));
        return preferences;
    }
}
