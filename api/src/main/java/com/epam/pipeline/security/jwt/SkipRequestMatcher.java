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

package com.epam.pipeline.security.jwt;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class SkipRequestMatcher implements RequestMatcher {
    private OrRequestMatcher skipMatcher;

    protected SkipRequestMatcher(OrRequestMatcher requestMatcher) {
        this.skipMatcher = requestMatcher;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return !this.skipMatcher.matches(request);
    }

    public static RequestMatcherBuilder builder() {
        return new RequestMatcherBuilder();
    }

    public static class RequestMatcherBuilder {
        private List<RequestMatcher> skipMatchers = new ArrayList<>();

        public RequestMatcherBuilder skipUrls(List<String> skipUrls) {
            skipUrls.forEach(url -> skipMatchers.add(new AntPathRequestMatcher(url)));

            return this;
        }

        public RequestMatcherBuilder skipMethod(String skipMethod) {
            skipMatchers.add(request -> skipMethod.equals(request.getMethod()));

            return this;
        }

        public SkipRequestMatcher build() {
            return new SkipRequestMatcher(new OrRequestMatcher(skipMatchers));
        }
    }
}
