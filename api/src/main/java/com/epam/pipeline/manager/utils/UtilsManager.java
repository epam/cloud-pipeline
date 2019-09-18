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

package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UtilsManager {

    private static final String SSH_URL_TEMPLATE = "%s://%s:%d/ssh/pipeline/%d";
    private static final String FSBROWSER_URL_TEMPLATE = "%s://%s:%d/fsbrowser/%d";

    @Autowired
    private KubernetesManager kubeManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Value("${kube.edge.label}")
    private String edgeLabel;

    public String buildSshUrl(Long runId) {
        return buildUrl(SSH_URL_TEMPLATE, runId);
    }

    public String buildFSBrowserUrl(Long runId) {
        return buildUrl(FSBROWSER_URL_TEMPLATE, runId);
    }

    private String buildUrl(String template, Long runId) {
        ServiceDescription service = kubeManager.getServiceByLabel(edgeLabel);
        if (service == null) {
            throw new IllegalArgumentException("Edge server is not registered in the cluster.");
        }
        return String.format(template, service.getScheme(), service.getIp(), service.getPort(), runId);
    }
    
    public List<DefaultSystemParameter> getSystemParameters() {
        List<DefaultSystemParameter> defaultSystemParameterList = preferenceManager.getPreference(
            SystemPreferences.LAUNCH_SYSTEM_PARAMETERS);
        return defaultSystemParameterList != null ? defaultSystemParameterList : Collections.emptyList();
    }
}
