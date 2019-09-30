package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.entity.TesTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@SuppressWarnings("unused")
@Component
public class TesTokenInterceptor implements HandlerInterceptor {
    private static final String HTTP_AUTH_COOKIE = "HttpAuthorization";

    private TesTokenHolder tesTokenHolder;

    @Value("${cloud.pipeline.token}")
    private String defaultPipelineToken;

    private IpAddressMatcher ipAddressMatcher;

    @Autowired
    public TesTokenInterceptor(TesTokenHolder tesTokenHolder,
                               @Value("${security.allowed.client.ip.range}") String ipRange) {
        this.tesTokenHolder = tesTokenHolder;
        ipAddressMatcher = new IpAddressMatcher(ipRange);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (checkRequestForToken(request).isPresent()) {
            tesTokenHolder.setToken(checkRequestForToken(request).get());
            return true;
        } else if (checkClientHostAddress(request) && Strings.isNotEmpty(defaultPipelineToken)) {
            tesTokenHolder.setToken(defaultPipelineToken);
            return true;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    private Optional<String> checkRequestForToken(HttpServletRequest request) {
        if (StringUtils.isNotEmpty(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            return Optional.of(request.getHeader(HttpHeaders.AUTHORIZATION));
        } else if (ArrayUtils.isNotEmpty(request.getCookies())) {
            return Arrays.stream(request.getCookies()).filter(cookie ->
                    cookie.getName().equalsIgnoreCase(HTTP_AUTH_COOKIE))
                    .map(Cookie::getValue).findFirst();
        }
        return Optional.empty();
    }

    private boolean checkClientHostAddress(HttpServletRequest request) {
        return ipAddressMatcher != null && ipAddressMatcher.matches(request);
    }
}
