/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dto.async;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@JsonDeserialize(using = AsyncTaskStatusDeserializer.class)
public enum AsyncTaskStatus {

    PENDING,
    RUNNING,
    CANCEL_REQUESTED,
    STOPPING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED;

    private static final Map<AsyncTaskStatus, Set<AsyncTaskStatus>> TRANSITIONS = transitions();
    private static final Set<AsyncTaskStatus> ACTIVE = Collections.unmodifiableSet(
            EnumSet.of(PENDING, RUNNING, CANCEL_REQUESTED, STOPPING));
    private static final Set<AsyncTaskStatus> NOT_REPORTABLE = Collections.unmodifiableSet(
            EnumSet.of(PENDING, RUNNING, CANCEL_REQUESTED));

    public boolean isReportable() {
        return !NOT_REPORTABLE.contains(this);
    }

    public boolean canMoveTo(final AsyncTaskStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public static Set<AsyncTaskStatus> activeStatuses() {
        return ACTIVE;
    }

    private static Map<AsyncTaskStatus, Set<AsyncTaskStatus>> transitions() {
        final Map<AsyncTaskStatus, Set<AsyncTaskStatus>> transitions = new EnumMap<>(AsyncTaskStatus.class);
        transitions.put(PENDING, EnumSet.of(RUNNING, CANCELLED));
        transitions.put(RUNNING, EnumSet.of(SUCCESS, FAILED, CANCEL_REQUESTED, STOPPING));
        transitions.put(CANCEL_REQUESTED, EnumSet.of(STOPPING, FAILED));
        transitions.put(STOPPING, EnumSet.of(CANCELLED, FAILED));
        transitions.put(SUCCESS, EnumSet.of(EXPIRED));
        transitions.put(FAILED, EnumSet.noneOf(AsyncTaskStatus.class));
        transitions.put(CANCELLED, EnumSet.noneOf(AsyncTaskStatus.class));
        transitions.put(EXPIRED, EnumSet.noneOf(AsyncTaskStatus.class));
        return Collections.unmodifiableMap(transitions);
    }
}
