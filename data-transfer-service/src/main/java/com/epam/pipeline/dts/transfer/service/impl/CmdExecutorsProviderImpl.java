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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.cmd.EnvironmentCmdExecutor;
import com.epam.pipeline.cmd.PlainCmdExecutor;
import com.epam.pipeline.cmd.QsubCmdExecutor;
import com.epam.pipeline.dts.transfer.service.CmdExecutorsProvider;

import java.util.Map;

public class CmdExecutorsProviderImpl implements CmdExecutorsProvider {
    @Override
    public CmdExecutor getCmdExecutor() {
        return new PlainCmdExecutor();
    }

    @Override
    public CmdExecutor getQsubCmdExecutor(final CmdExecutor cmdExecutor, final String qsubTemplate) {
        return new QsubCmdExecutor(cmdExecutor, qsubTemplate);
    }

    @Override
    public CmdExecutor getEnvironmentCmdExecutor(final CmdExecutor cmdExecutor, final Map<String, String> envVars) {
        return new EnvironmentCmdExecutor(cmdExecutor, envVars);
    }
}
