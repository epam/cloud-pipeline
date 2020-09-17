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
import com.epam.pipeline.dts.listing.model.ListingItemsPaging;
import com.epam.pipeline.dts.listing.service.ListingService;
import com.epam.pipeline.dts.security.service.SecurityService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

@RequiredArgsConstructor
public class ImpersonatingLocalListingService implements ListingService {

    private final SecurityService securityService;
    private final CmdExecutor cmdExecutor;
    private final String listTemplate;

    @Override
    public ListingItemsPaging list(@NotNull Path path, Integer pageSize, String marker) {
        final String localUser = securityService.getLocalUser();
        verifyPagingAttributes(pageSize);
        final long offset = StringUtils.isNumeric(marker) ? Long.parseLong(marker) : 1;
        Assert.isTrue(offset > 0, "Page marker must be greater than null");
        final long size = normalizePageSize(pageSize);
        try {
            final String output = cmdExecutor.executeCommand(list(path, offset, size), localUser);
            return JsonMapper.parseData(output, new TypeReference<ListingItemsPaging>() {});
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("An error occurred during listing local file %s.", path.toAbsolutePath()), e);
        }
    }

    private void verifyPagingAttributes(Integer pageSize) {
        Assert.isTrue(pageSize == null || pageSize > 0,
                String.format("Invalid paging attributes: page size - %s. Page size must be grater then zero,",
                        pageSize));
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null ? Long.MAX_VALUE : pageSize;
    }

    private String list(final @NotNull Path path, final long offset, final long size) {
        return String.format(listTemplate, path, offset, size);
    }
}
