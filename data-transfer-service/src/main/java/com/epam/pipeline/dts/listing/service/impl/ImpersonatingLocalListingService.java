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

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dts.listing.configuration.ListingPreference;
import com.epam.pipeline.dts.listing.model.ListingItemsPaging;
import com.epam.pipeline.dts.listing.service.ListingService;
import com.epam.pipeline.dts.security.service.SecurityService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

/**
 * Local file system listing service which impersonates currently authenticated user to perform file system operations.
 * 
 * It requires dependent cmd executor to support user impersonation.
 */
@Service
@ConditionalOnProperty(value = "dts.impersonation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ImpersonatingLocalListingService implements ListingService {

    private final SecurityService securityService;
    private final CmdExecutor listingCmdExecutor;
    private final ListingPreference preference;

    @Override
    public ListingItemsPaging list(@NotNull final Path path, final Integer pageSize, final String marker) {
        final long size = pageSize == null ? Long.MAX_VALUE : pageSize;
        final long offset = StringUtils.isNumeric(marker) ? Long.parseLong(marker) : 1;
        Assert.isTrue(size > 0, String.format(
                "Invalid paging attributes: page size - %s. Page size must be grater then zero,", pageSize));
        Assert.isTrue(offset > 0, "Page marker must be greater than zero");
        try {
            final String localUser = securityService.getLocalUser();
            final String listingOutput = listingCmdExecutor.executeCommand(list(path, offset, size), localUser);
            return parsed(listingOutput);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("An error occurred during listing local file %s.", path.toAbsolutePath()), e);
        }
    }

    private String list(@NotNull final Path path, final long offset, final long size) {
        return String.format(preference.getListCommand(), preference.getListScript(), path, offset, size);
    }

    private ListingItemsPaging parsed(final String listingOutput) {
        return JsonMapper.parseData(listingOutput, new TypeReference<ListingItemsPaging>() {});
    }
}
