/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.util;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMapOf;

public final class CustomMatchers {

    private CustomMatchers() {
    }

    public static <T> Matcher<Collection<T>> isEmpty() {
        return new BaseMatcher<Collection<T>>() {
            @Override
            public boolean matches(final Object item) {
                return item instanceof Collection
                        && ((Collection) item).isEmpty();
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("collection is empty");
            }
        };
    }

    public static <T> BaseMatcher<T> matches(final Predicate<T> test) {
        return new BaseMatcher<T>() {

            @Override
            public void describeTo(final Description description) {
                description.appendText("custom matcher");
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(final Object item) {
                return test.test((T) item);
            }
        };
    }

    public static List<Long> anyLongList() {
        return anyListOf(Long.class);
    }

    public static List<String> anyStringList() {
        return anyListOf(String.class);
    }

    public static Map<String, String> anyStringMap() {
        return anyMapOf(String.class, String.class);
    }
}
