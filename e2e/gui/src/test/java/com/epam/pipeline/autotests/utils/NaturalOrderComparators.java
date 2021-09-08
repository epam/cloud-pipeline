/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;

public class NaturalOrderComparators {
    private static final String DIGIT_AND_DECIMAL_REGEX = "[^\\d.]";

    private NaturalOrderComparators() {}

    public static Comparator<String> createNaturalOrderRegexComparator() {
        return Comparator.comparingDouble(NaturalOrderComparators::parseStringToNumber);
    }

    private static double parseStringToNumber(final String input) {

        final String digitsOnly = input.replaceAll(DIGIT_AND_DECIMAL_REGEX, StringUtils.EMPTY);

        if (StringUtils.EMPTY.equals(digitsOnly)) {
            return 0;
        }
        try {
            return Double.parseDouble(digitsOnly);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
