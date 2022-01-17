/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
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
 * limitations under the License.
 */

package com.epam.pipeline.monitor.service.preference;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PreferencesService {
    private final CloudPipelineAPIClient client;

    private final ConcurrentHashMap<String, Preference> preferences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subject<String>> subjectMap = new ConcurrentHashMap<>();

    public PreferencesService(final CloudPipelineAPIClient client) {
        this.client = client;
    }

    @Scheduled(fixedDelayString = "${refresh.preferences:60000}")
    public void refreshPreferences() {
        final Map<String, Preference> loadedPreferences = client.getAllPreferences().stream()
                .collect(Collectors.toMap(Preference::getName, Function.identity()));
        loadedPreferences.entrySet()
                .stream()
                .filter(preference -> preferenceChanged(preference.getValue()))
                .forEach(preference -> notifyPreferenceChanged(preference.getKey(),
                        preference.getValue().getValue()));
        preferences.clear();
        preferences.putAll(loadedPreferences);
    }

    public Observable<String> getObservablePreference(final String preference) {
        Subject<String> preferenceSubject;
        if (!subjectMap.contains(preference)) {
            preferenceSubject = PublishSubject.create();
            subjectMap.put(preference, preferenceSubject);
        } else {
            preferenceSubject = subjectMap.get(preference);
        }

        return preferenceSubject;
    }

    private void notifyPreferenceChanged(final String preferenceName, final String preferenceValue) {
        final Subject<String> subject = subjectMap.get(preferenceName);
        if (subject != null) {
            subject.onNext(preferenceValue);
        }
    }

    private boolean preferenceChanged(final Preference preference) {
        if (Objects.isNull(preference)) {
            return false;
        }
        if (StringUtils.isBlank(preference.getValue())) {
            return false;
        }
        if (!preferences.containsKey(preference.getName())) {
            return false;
        }
        return !Objects.equals(preferences.get(preference.getName()).getValue(), preference.getValue());
    }
}
