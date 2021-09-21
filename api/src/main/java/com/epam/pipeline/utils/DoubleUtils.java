/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import org.apache.commons.math3.util.Precision;

public interface DoubleUtils {

    double DELTA = 0.001;

    //inclusive
    static boolean between(double from, double to, double value) {
        return compare(value, from) >= 0 && compare(value, to) <= 0;
    }

    static int compare(double d1, double d2) {
        return Precision.compareTo(d1, d2, DELTA);
    }
}
