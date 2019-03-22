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

package com.epam.pipeline.dts.submission.service.sge.impl;

import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.model.cluster.QHosts;
import com.epam.pipeline.dts.submission.model.cluster.SGEHost;
import com.epam.pipeline.dts.submission.model.execution.SGEJob;
import com.epam.pipeline.dts.submission.service.sge.QdelCommand;
import com.epam.pipeline.dts.submission.service.sge.QhostCommand;
import com.epam.pipeline.dts.submission.service.sge.QstatCommand;
import com.epam.pipeline.dts.submission.service.sge.SGEService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SGEServiceImpl implements SGEService {

    private final String sgeQueueName;
    private final QhostCommand qhostCommand;
    private final QstatCommand qstatCommand;
    private final QdelCommand qdelCommand;

    public SGEServiceImpl(final @Value("${dts.submission.queue.name}") String sgeQueueName,
                          final QhostCommand qhostCommand,
                          final QstatCommand qstatCommand,
                          final QdelCommand qdelCommand) {
        this.sgeQueueName = sgeQueueName;
        this.qhostCommand = qhostCommand;
        this.qstatCommand = qstatCommand;
        this.qdelCommand = qdelCommand;
    }

    @Override
    public QHosts getHosts() throws SGECmdException {
        return filterHosts(qhostCommand.getHosts());
    }

    @Override
    public SGEJob getJobInfo(final String jobId) throws SGECmdException {
        return qstatCommand.describeSGEJob(jobId);
    }

    @Override
    public void stopJob(final String jobId) throws SGECmdException {
        qdelCommand.stopSGEJob(jobId);
    }

    private QHosts filterHosts(final QHosts allHosts) {
        List<SGEHost> filtered = ListUtils.emptyIfNull(allHosts.getHosts())
                .stream()
                .filter(host -> ListUtils.emptyIfNull(host.getHostQueues())
                        .stream().anyMatch(queue -> sgeQueueName.equals(queue.getName())))
                .map(host -> SGEHost.builder()
                        .name(host.getName())
                        .hostAttributes(host.getHostAttributes())
                        .hostQueues(host.getHostQueues().stream()
                                .filter(queue -> sgeQueueName.equals(queue.getName()))
                                .collect(Collectors.toList()))
                        .build()).collect(Collectors.toList());
        return new QHosts(filtered);
    }
}
