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
package com.epam.pipeline.elasticsearchagent;

import com.mchange.util.AssertException;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unchecked")
final class VerificationUtils {

    static void verifyStringArray(final Collection<String> expected, final Object object) {
        ArrayList<String> actual = toStringArray(object);

        if (CollectionUtils.isEmpty(expected)) {
            if (CollectionUtils.isNotEmpty(actual)) {
                throw new AssertException("Expected list is empty but actual not");
            }
            return;
        }

        Assert.assertEquals(expected.size(), actual.size());
        expected.forEach(element -> Assert.assertTrue(actual.contains(element)));
    }

    static void verifyArray(final List<?> expected, final List<?> actual) {
        if (CollectionUtils.isEmpty(expected)) {
            if (CollectionUtils.isNotEmpty(actual)) {
                throw new AssertException("Expected list is empty but actual not");
            }
            return;
        }
        Assert.assertEquals(expected.size(), actual.size());
    }

    private static ArrayList<String> toStringArray(final Object object) {
        return new ArrayList<>((Collection<? extends String>) object);
    }

    private VerificationUtils() {
        // no-op
    }
}
