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

package com.epam.pipeline.util;

import java.util.function.Predicate;

import static org.junit.Assert.fail;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
public final class CustomAssertions {

    private CustomAssertions() {}

    public static void assertThrows(final Class<? extends Throwable> expectedExceptionClass, final Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (expectedExceptionClass.isInstance(e)) {
                return;
            } else {
                throw new AssertionError(
                        String.format("Expected exception %s was not thrown, but another exception was: %s.",
                                expectedExceptionClass.getSimpleName(), e.getClass().getSimpleName()), e);
            }
        }
        fail(String.format("Expected exception %s was not thrown.", expectedExceptionClass.getSimpleName()));
    }

    public static void assertThrows(final Predicate<Throwable> exceptionPredicate, final Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (exceptionPredicate.test(e)) {
                return;
            } else {
                throw new AssertionError("Thrown exception does not match the given predicate.", e);
            }
        }
        fail("Expected exception but nothing was thrown.");
    }

    public static void assertThrows(final Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            return;
        }
        fail("Exception was expected but nothing was thrown.");
    }

    public static void assertThrowsChecked(final Class<? extends Throwable> expectedExceptionClass,
                                           final CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (expectedExceptionClass.isInstance(e)) {
                return;
            } else {
                throw new AssertionError(
                        String.format("Expected exception %s was not thrown, but another exception was: %s.",
                                expectedExceptionClass.getSimpleName(), e.getClass().getSimpleName()), e);
            }
        }
        fail(String.format("Expected exception %s was not thrown.", expectedExceptionClass.getSimpleName()));
    }
}
