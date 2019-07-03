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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents system pre-defined preference. To be used with PreferenceManager get preference methods.
 * Contains shorthand preference typed classes to simplify type validation.
 */
@Getter
public abstract class AbstractSystemPreference<T> {
    private String key;
    private T defaultValue;
    private String group;
    private BiPredicate<String, Map<String, Preference>> validator;
    private final PreferenceType type;
    private Map<String, AbstractSystemPreference> dependencies = Collections.emptyMap();

    public AbstractSystemPreference(String key, T defaultValue, String group, BiPredicate<String,
            Map<String, Preference>> validator, PreferenceType type, AbstractSystemPreference... dependencies) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.group = group;
        this.validator = validator;
        this.type = type;
        if (dependencies != null) {
            this.dependencies = Arrays.stream(dependencies)
                    .collect(Collectors.toMap(AbstractSystemPreference::getKey, preference -> preference));
        }
    }

    /**
     * Parses the string value of a preference to a typed value of this AbstractSystemPreference
     * @param value a string value
     * @return a typed value of this AbstractSystemPreference
     */
    public abstract T parse(String value);

    public boolean validate(String value, Map<String, Preference> dependentPreferences) {
        return type.validate(value) && getValidator().test(value, dependentPreferences);
    }

    public Preference toPreference() {
        return new Preference(key, defaultValue == null ? null : defaultValue.toString(), group, null, type,
                false);
    }

    void setValidator(BiPredicate<String, Map<String, Preference>> validator) {
        this.validator = validator;
    }

    void setDependencies(AbstractSystemPreference... dependencies) {
        if (dependencies != null) {
            this.dependencies = Arrays.stream(dependencies)
                    .collect(Collectors.toMap(AbstractSystemPreference::getKey, preference -> preference));
        }
    }

    void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    public static class StringPreference extends AbstractSystemPreference<String> {
        public StringPreference(String key, String defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.STRING, dependencies);
        }

        @Override
        public String parse(String value) {
            return value;
        }
    }

    public static class IntPreference extends AbstractSystemPreference<Integer> {
        public IntPreference(String key, Integer defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.INTEGER, dependencies);
        }

        @Override
        public Integer parse(String value) {
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return Integer.parseInt(value);
        }
    }

    public static class MemorySizePreference extends AbstractSystemPreference<Integer> {

        public static final String MEMORY_SIZE_PATTERN = "(\\d+)([k|m|g])";
        public static final Pattern SIZE_PATTERN = Pattern.compile(MEMORY_SIZE_PATTERN);
        public static final int KILO = 1024;

        public MemorySizePreference(String key, Integer defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.INTEGER, dependencies);
        }

        @Override
        public Integer parse(String value) {
            if (StringUtils.isBlank(value)) {
                return null;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Integer result = parseWithPostfix(value);
                return result > 0 ? result : Integer.MAX_VALUE;
            }
        }

        private Integer parseWithPostfix(final String value) {
            final Matcher matcher = SIZE_PATTERN.matcher(value.trim());
            if (matcher.matches()) {
                final int number = Integer.parseInt(matcher.group(1));
                final String prefix = matcher.group(2);
                switch (prefix) {
                    case "k":
                        return returnMaxIntegerIfOverfilled(number * KILO);
                    case "m":
                        return returnMaxIntegerIfOverfilled(number * KILO * KILO);
                    case "g":
                        return returnMaxIntegerIfOverfilled(number * KILO * KILO * KILO);
                    default:
                        throw new IllegalArgumentException("Wrong memory size parameter: " + value);
                }
            } else {
                throw new IllegalArgumentException("Wrong memory size parameter: " + value);
            }
        }

        private Integer returnMaxIntegerIfOverfilled(final int value) {
            return value > 0 ? value : Integer.MAX_VALUE;
        }
    }

    public static class LongPreference extends AbstractSystemPreference<Long> {
        public LongPreference(String key, Long defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.LONG, dependencies);
        }

        @Override
        public Long parse(String value) {
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return Long.parseLong(value);
        }
    }

    public static class FloatPreference extends AbstractSystemPreference<Float> {
        public FloatPreference(String key, Float defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.FLOAT, dependencies);
        }

        @Override
        public Float parse(String value) {
            if (StringUtils.isBlank(value)) {
                return null;
            }

            return Float.parseFloat(value);
        }
    }

    public static class DoublePreference extends AbstractSystemPreference<Double> {
        public DoublePreference(String key, Double defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.FLOAT, dependencies);
        }

        @Override
        public Double parse(String value) {
            if (StringUtils.isBlank(value)) {
                return null;
            }

            return Double.parseDouble(value);
        }
    }

    public static class BooleanPreference extends AbstractSystemPreference<Boolean> {
        public BooleanPreference(String key, Boolean defaultValue, String group, BiPredicate<String,
                Map<String, Preference>> validator, AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.BOOLEAN, dependencies);
        }

        @Override
        public Boolean parse(String value) {
            return Boolean.parseBoolean(value);
        }
    }

    public static class ObjectPreference<T> extends AbstractSystemPreference<T> {
        private TypeReference<T> typeReference;

        public ObjectPreference(String key, T defaultValue, TypeReference<T> typeReference, String group,
                                BiPredicate<String, Map<String, Preference>> validator,
                                AbstractSystemPreference... dependencies) {
            super(key, defaultValue, group, validator, PreferenceType.OBJECT, dependencies);
            this.typeReference = typeReference;
        }

        @Override
        public T parse(String value) {
            return JsonMapper.parseData(value, typeReference);
        }
    }
}

