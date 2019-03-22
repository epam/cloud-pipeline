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

package com.epam.pipeline.manager.parallel;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ParallelExecutorServiceTest extends AbstractManagerTest {
    private static final int TIMEOUT = 2000;

    @Autowired
    private ParallelExecutorService parallelExecutorService;

    @Autowired
    private PreferenceManager preferenceManager;

    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    public void testGetExecutorService() throws InterruptedException {
        ExecutorService service1 = parallelExecutorService.getExecutorService();

        Preference maxThreads = SystemPreferences.CLUSTER_NODEUP_MAX_THREADS.toPreference();
        maxThreads.setValue("2");
        preferenceManager.update(Collections.singletonList(maxThreads));

        Thread.sleep(TIMEOUT); // Wait for a new executor to be created

        ExecutorService service2 = parallelExecutorService.getExecutorService();
        Assert.assertNotEquals(service1, service2);
    }
}