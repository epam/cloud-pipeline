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

package com.epam.pipeline.dts.listing.service.impl;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.listing.configuration.ListingPreference;
import com.epam.pipeline.dts.listing.exception.LocalListingException;
import com.epam.pipeline.dts.listing.model.ListingItemsPaging;
import com.epam.pipeline.dts.listing.rest.dto.ItemsListingRequestDTO;
import com.epam.pipeline.dts.listing.service.ListingService;
import com.epam.pipeline.dts.security.service.SecurityService;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Local file system listing service which impersonates currently authenticated user to perform file system operations.
 * 
 * It requires dependent cmd executor to support user impersonation.
 */
@Service
@ConditionalOnProperty(value = "dts.impersonation.enabled", havingValue = "true", matchIfMissing = true)
public class ImpersonatingLocalListingService implements ListingService {

    private final SecurityService securityService;
    private final CmdExecutor listingCmdExecutor;
    private final ListingPreference preference;
    private final String dtsNameMetadataKey;

    public ImpersonatingLocalListingService(final SecurityService securityService,
                                            final CmdExecutor listingCmdExecutor,
                                            final ListingPreference preference,
                                            @Value("${dts.impersonation.name.metadata.key}")
                                            final String dtsNameMetadataKey) {
        this.securityService = securityService;
        this.listingCmdExecutor = listingCmdExecutor;
        this.preference = preference;
        this.dtsNameMetadataKey = dtsNameMetadataKey;
    }

    @Override
    public ListingItemsPaging list(final ItemsListingRequestDTO request) {
        final long size = request.getPageSize() == null ? Long.MAX_VALUE : request.getPageSize();
        final long offset = StringUtils.isNumeric(request.getMarker()) ? Long.parseLong(request.getMarker()) : 1;
        Assert.isTrue(size > 0, String.format(
                "Invalid paging attributes: page size - %s. Page size must be greater then zero,", 
                request.getPageSize()));
        Assert.isTrue(offset > 0, "Page marker must be greater than zero");
        final String impersonatingUser = getImpersonatingUser(request.getCredentials());
        try {
            final String listingOutput = listingCmdExecutor.executeCommand(
                    list(request.getPath(), offset, size), impersonatingUser);
            return parsed(listingOutput);
        } catch (CmdExecutionException e) {
            throw new LocalListingException(e.getRootMessage(), e);
        }
    }

    private String getImpersonatingUser(final PipelineCredentials credentials) {
        return Optional.ofNullable(credentials)
                .filter(PipelineCredentials::isComplete)
                .map(CloudPipelineAPIClient::from)
                .flatMap(client -> client.getUserMetadataValueByKey(dtsNameMetadataKey))
                .orElseGet(securityService::getImpersonatingUser);
    }

    private String list(@NotNull final Path path, final long offset, final long size) {
        return String.format(preference.getListCommand(), preference.getListScript(), path, offset, size);
    }

    private ListingItemsPaging parsed(final String listingOutput) {
        return JsonMapper.parseData(listingOutput, new TypeReference<ListingItemsPaging>() {});
    }
}
