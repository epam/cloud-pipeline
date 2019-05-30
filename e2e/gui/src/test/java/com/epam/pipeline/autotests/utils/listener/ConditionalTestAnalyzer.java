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
package com.epam.pipeline.autotests.utils.listener;

import com.epam.pipeline.autotests.utils.C;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;

import java.lang.reflect.Method;
import java.util.stream.Stream;

public class ConditionalTestAnalyzer implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult) {
        final Method resultMethod = testResult.getMethod().getConstructorOrMethod().getMethod();
        if (!resultMethod.isAnnotationPresent(CloudProviderOnly.class)) {
            return;
        }
        final Cloud[] values = resultMethod.getAnnotation(CloudProviderOnly.class).values();
        Stream.of(values)
            .filter(v -> v.name().toLowerCase().equals(C.CLOUD_PROVIDER))
            .findFirst()
            .<SkipException>orElseThrow(() -> {
                throw new SkipException(String.format("This test is not supported by %s provider", C.CLOUD_PROVIDER));
            });
    }

    @Override
    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult) {
        // no op
    }
}
