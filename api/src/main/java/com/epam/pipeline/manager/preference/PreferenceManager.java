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

package com.epam.pipeline.manager.preference;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.preference.PreferenceDao;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.ObjectPreference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A service class, that incorporate business logic, connected with preferences. Preferences can be pre-defined
 * ({@link AbstractSystemPreference} or user-defined. All of them can be set by user. This class provides a nadful set
 * of methods to query these properties from other application components. Use getPreference(...) set of methods to load
 * SystemPreferences. Use getIntPreference and etc to load custom preferences.
 */
@Service
@DependsOn({"flyway", "flywayInitializer"})
public class PreferenceManager {
    @Autowired
    private PreferenceDao preferenceDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private SystemPreferences systemPreferences;

    private ConcurrentHashMap<AbstractSystemPreference, Subject> subjectMap = new ConcurrentHashMap<>();

    /**
     * Updates a list of preferences. Notifies all observers, if there are some for any of the updated preferences
     * @param preferences a list of preferences to update
     * @return updated preferences
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Preference> update(List<Preference> preferences) {
        Assert.isTrue(preferences.stream().allMatch(p -> StringUtils.isNotBlank(p.getName())),
                messageHelper.getMessage(MessageConstants.ERROR_PREFERENCE_NAME_NOT_SPECIFIED));

        systemPreferences.validate(preferences);

        preferences.forEach(p -> {
            preferenceDao.upsertPreference(mergeWithDefaults(p));
            notifyPreferenceChanged(p);
        });

        return preferences;
    }

    private void notifyPreferenceChanged(Preference p) {
        Optional<AbstractSystemPreference<?>> opt = SystemPreferences.getSystemPreference(p.getName());
        opt.ifPresent((sysPref) -> {
            Subject subject = subjectMap.get(sysPref);
            if (subject != null) {
                subject.onNext(sysPref.parse(p.getValue()));
            }
        });
    }

    private Preference mergeWithDefaults(Preference preference) {
        Optional<AbstractSystemPreference<?>> systemPref = SystemPreferences.getSystemPreference(preference.getName());
        if (!systemPref.isPresent()) {
            Assert.notNull(preference.getType(), messageHelper.getMessage(
                MessageConstants.ERROR_PREFERENCE_TYPE_NOT_SPECIFIED));
            return preference;
        }

        Preference origin = systemPref.get().toPreference();
        origin.setValue(preference.getValue()); // Just set the new value
        origin.setVisible(preference.isVisible());

        return origin;
    }

    public Optional<Preference> load(String name) {
        Preference preference = preferenceDao.loadPreferenceByName(name);
        if (preference == null) {
            return SystemPreferences.getSystemPreference(name)
                .map(AbstractSystemPreference::toPreference);
        }

        return Optional.of(preference);
    }

    public Collection<Preference> loadAll() {
        Map<String, Preference> preferences = preferenceDao.loadAllPreferences().stream()
            .collect(Collectors.toMap(Preference::getName, p -> p));

        systemPreferences.getSystemPreferences().forEach(p -> preferences.putIfAbsent(p.getKey(), p.toPreference()));
        return preferences.values();
    }

    /**
     * Loads only preferences, that are visible to non-admin users
     * @return a List of Preference
     */
    public Collection<Preference> loadVisible() {
        return preferenceDao.loadVisiblePreferences();
    }

    /**
     * Deletes preferences value, restoring it to default. Notifies all preference observers, if there are some
     * @param name preference's name
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(String name) {
        preferenceDao.deletePreference(name);

        Optional<AbstractSystemPreference<?>> opt = SystemPreferences.getSystemPreference(name);
        opt.ifPresent((sysPref) -> {
            Subject subject = subjectMap.get(sysPref);
            if (subject != null) {
                subject.onNext(sysPref.getDefaultValue());
            }
        });
    }

    public Integer getIntPreference(String name) {
        return parsePreferenceIfPresent(name, PreferenceType.INTEGER, Integer::parseInt);
    }

    private <T> T parsePreferenceIfPresent(String name, PreferenceType type, Function<String, T> castFunction) {
        return load(name)
                .map(preference -> {
                    Assert.isTrue(preference.getType() == type,
                            messageHelper.getMessage(MessageConstants.ERROR_PREFERENCE_WITH_NAME_HAS_DIFFERENT_TYPE,
                                    name, preference.getType().name()));
                    return preference.get(castFunction);
                })
                .orElse(null);
    }

    public Float getFloatPreference(String name) {
        return parsePreferenceIfPresent(name, PreferenceType.FLOAT, Float::parseFloat);
    }

    public Boolean getBooleanPreference(String name) {
        return parsePreferenceIfPresent(name, PreferenceType.BOOLEAN, Boolean::parseBoolean);
    }

    public String getStringPreference(String name) {
        return parsePreferenceIfPresent(name, PreferenceType.STRING, s -> s);
    }

    public Map<String, Object> getObjectPreference(String name) {
        return parsePreferenceIfPresent(name, PreferenceType.OBJECT, value -> JsonMapper.parseData(
            value, new TypeReference<Map<String, Object>>(){}));
    }

    /**
     * An generic method to load a value of any AbstractSystemPreference
     * @param systemPreference a preference to load value of
     * @param <E> a type of preference value
     * @param <T> a type of preference
     * @return typed value of AbstractSystemPreference
     */
    public <E, T extends AbstractSystemPreference<E>> E getPreference(T systemPreference) {
        Preference pref = getSystemPreference(systemPreference);
        return systemPreference.parse(pref.getValue());
    }

    /**
     * A generic method to find an optional value of any AbstractSystemPreference
     * @param systemPreference a preference to load value of
     * @return optional of typed value of AbstractSystemPreference
     */
    public <E, T extends AbstractSystemPreference<E>> Optional<E> findPreference(T systemPreference) {
        return Optional.ofNullable(getPreference(systemPreference));
    }

    /**
     * An generic method to load an Observable of any AbstractSystemPreference's value. An Observable exposes preference
     * value changes. Use this method only if there's need to do some actions on preference changes.
     * @param preference a preference to load value of
     * @param <E> a type of preference value
     * @param <T> a type of preference
     * @return an Observable of AbstractSystemPreference's value
     */
    public <E, T extends AbstractSystemPreference<E>> Observable<E> getObservablePreference(T preference) {
        Subject<E> preferenceSubject;
        if (!subjectMap.contains(preference)) {
            preferenceSubject = PublishSubject.create();
            subjectMap.put(preference, preferenceSubject);
        } else {
            preferenceSubject = (Subject<E>) subjectMap.get(preference);
        }

        return preferenceSubject;
    }

    public Preference getSystemPreference(AbstractSystemPreference systemPreference) {
        Preference loaded = load(systemPreference.getKey()).orElseThrow(() ->
                new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_PREFERENCE_WITH_NAME_NOT_FOUND, systemPreference.getKey())));
        Assert.isTrue(loaded.getType() == systemPreference.getType(),
                      messageHelper.getMessage(MessageConstants.ERROR_PREFERENCE_WITH_NAME_HAS_DIFFERENT_TYPE,
                              loaded.getName(), loaded.getType().name()));
        return loaded;
    }

    /**
     * Will try to map an ObjectPreference with a specified TypeReference and return a result of specified type.
     * Will throw a {@link com.fasterxml.jackson.databind.JsonMappingException} if mapping is not successful.
     * @param preference an object preference, which value to get
     * @param typeReference a TypeReference, that specifies the type to map
     * @param <T> expected type
     * @return a result of specified type
     */
    public <T> T getObjectPreferenceAs(final ObjectPreference preference,
                                       final TypeReference<T> typeReference) {
        return getObjectPreferenceAs(preference,
            v -> JsonMapper.parseData(v, typeReference));
    }

    /**
     * Will try to map an ObjectPreference with a specified TypeReference using given object mapper
     * and return a result of specified type.
     * Will throw a {@link com.fasterxml.jackson.databind.JsonMappingException} if mapping is not successful.
     * @param preference an object preference, which value to get.
     * @param typeReference a TypeReference, that specifies the type to map.
     * @param objectMapper an object mapper to be used while parsing a preference string.
     * @param <T> expected type.
     * @return a result of specified type.
     */
    public <T> T getObjectPreferenceAs(final ObjectPreference preference,
                                       final TypeReference<T> typeReference,
                                       final ObjectMapper objectMapper) {
        return getObjectPreferenceAs(preference,
            v -> JsonMapper.parseData(v, typeReference, objectMapper));
    }

    private <T> T getObjectPreferenceAs(final ObjectPreference preference,
                                        final Function<String, T> castFunction) {
        final Preference pref = getSystemPreference(preference);

        return pref.get(castFunction);
    }
}
