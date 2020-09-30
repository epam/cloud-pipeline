/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer;

import com.epam.pipeline.dts.transfer.configuration.TransferRestConfiguration;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.service.CmdExecutorsProvider;
import com.epam.pipeline.dts.transfer.service.DataUploader;
import com.epam.pipeline.dts.transfer.service.DataUploaderProvider;
import com.epam.pipeline.dts.transfer.service.PipelineCliProvider;
import com.epam.pipeline.dts.transfer.service.impl.AzureDataUploader;
import com.epam.pipeline.dts.transfer.service.impl.CmdExecutorsProviderImpl;
import com.epam.pipeline.dts.transfer.service.impl.DataUploaderProviderImpl;
import com.epam.pipeline.dts.transfer.service.impl.GSDataUploader;
import com.epam.pipeline.dts.transfer.service.impl.PipelineCliProviderImpl;
import com.epam.pipeline.dts.transfer.service.impl.S3DataUploader;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Controller;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootConfiguration
@EntityScan(basePackages = {"com.epam.pipeline.dts.transfer.model"})
@EnableJpaRepositories(basePackages = {"com.epam.pipeline.dts.transfer.repository"})
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TransferRestConfiguration.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class)})
public class TransferConfiguration {

    @Bean
    public DataUploaderProvider dataUploaderProvider(final List<DataUploader> providers) {
        final Map<StorageType, DataUploader> dataUploaders = CollectionUtils.isEmpty(providers)
                ? new EnumMap<>(StorageType.class)
                : providers.stream().collect(Collectors.toMap(DataUploader::getStorageType, Function.identity()));

        return new DataUploaderProviderImpl(dataUploaders);
    }

    @Bean
    public PipelineCliProvider pipelineCliProvider(final CmdExecutorsProvider cmdExecutorsProvider,
                                                   @Value("${dts.transfer.pipe.executable}")
                                                   final String pipelineCliExecutable,
                                                   @Value("${dts.transfer.pipe.cp.suffix:}") final String pipeCpSuffix,
                                                   @Value("${dts.transfer.grid.template}") final String qsubTemplate,
                                                   @Value("${dts.transfer.grid.upload}")
                                                       final boolean isGridUploadEnabled,
                                                   @Value("${dts.transfer.upload.force}") final boolean forceUpload,
                                                   @Value("${dts.transfer.upload.retry}") final int retryCount) {
        return new PipelineCliProviderImpl(cmdExecutorsProvider, pipelineCliExecutable, pipeCpSuffix, qsubTemplate,
                isGridUploadEnabled, forceUpload, retryCount);
    }

    @Bean
    public CmdExecutorsProvider cmdExecutorsProvider() {
        return new CmdExecutorsProviderImpl();
    }

    @Bean
    public DataUploader s3DataUploader(final PipelineCliProvider pipelineCliProvider) {
        return new S3DataUploader(pipelineCliProvider);
    }

    @Bean
    public DataUploader azureDataUploader(final PipelineCliProvider pipelineCliProvider) {
        return new AzureDataUploader(pipelineCliProvider);
    }
    
    @Bean
    public DataUploader gsDataUploader(final PipelineCliProvider pipelineCliProvider) {
        return new GSDataUploader(pipelineCliProvider);
    }
}
