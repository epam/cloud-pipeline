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

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.reactivex.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ParallelExecutorService {
    @Autowired
    private PreferenceManager preferenceManager;

    private volatile ExecutorService executorService;

    public ExecutorService getExecutorService() {
        ExecutorService instance = executorService;
        if (instance == null) {
            synchronized (this) {
                instance = executorService;
                if (instance == null) {
                    instance = createExecutorService();
                    executorService = instance;
                }
            }
        }
        return instance;
    }

    private ExecutorService createExecutorService() {
        int maxNodeUpThreads = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS);
        preferenceManager.getObservablePreference(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS)
            .observeOn(Schedulers.io())
            .subscribe((numThreads -> {
                synchronized (this) {
                    executorService.shutdown();
                    executorService = getExecutorService(maxNodeUpThreads);
                }
            }));

        return getExecutorService(maxNodeUpThreads);
    }

    private ExecutorService getExecutorService(final int maxNodeUpThreads) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNodeUpThreads);
        pool.prestartAllCoreThreads();
        return new DelegatingSecurityContextExecutorService(pool, SecurityContextHolder.getContext());
    }
}
