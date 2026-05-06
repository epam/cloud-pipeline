/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.github;

import org.apache.commons.collections4.ListUtils;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Common utils for GitHub.
 */
public final class GitHubUtils {
    public static final String AUTHORIZATION = "Authorization";
    public static final Integer LIMIT = 100;
    private static final String LINK = "link";
    private static final String REL_NEXT = "rel=\"next\"";

    private GitHubUtils() {
        // no-op
    }

    public static <T> List<T> pagination(final Function<Integer, Response<List<T>>> apiCall) {
        return pagination(apiCall, Function.identity());
    }

    public static <T, R> List<T> pagination(final Function<Integer, Response<R>> apiCall,
                                            final Function<R, List<T>> responseConverter) {
        int page = 1;
        Response<R> response = apiCall.apply(page);
        final List<T> values = new ArrayList<>(ListUtils.emptyIfNull(responseConverter.apply(response.body())));
        String link = response.headers().get(LINK);
        while (link != null && link.contains(REL_NEXT)) {
            page++;
            response = apiCall.apply(page);
            values.addAll(ListUtils.emptyIfNull(responseConverter.apply(response.body())));
            link = response.headers().get(LINK);
        }
        return values;
    }
}
