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

package com.epam.pipeline.security;

import com.epam.pipeline.security.saml.SAMLProxyAuthentication;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class SecurityLogAspect {

    public static final String CHANGING_PERMISSION_POINTCUT =
            "execution(* com.epam.pipeline.manager.security.GrantPermissionManager.setPermissions(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.deletePermissions(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.deleteAllPermissions(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.lockEntity(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.unlockEntity(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.changeOwner(..))";

    public static final String ANONYMOUS = "Anonymous";
    public static final String KEY_USER = "user";


    @Before(value = CHANGING_PERMISSION_POINTCUT)
    public void addUserInfoFromSecurityContext() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null) {
            ThreadContext.put(KEY_USER, context.getAuthentication() != null
                    ? context.getAuthentication().getName()
                    : ANONYMOUS);
        }
    }

    @Before(value = "execution(* com.epam.pipeline.security.saml.SAMLProxyAuthenticationProvider.authenticate(..)) " +
            "&& args(authentication,..)")
    public void addUserInfoFromSAMLProxy(JoinPoint joinPoint, Authentication authentication) {
        if (authentication != null) {
            SAMLProxyAuthentication auth = (SAMLProxyAuthentication) authentication;
            ThreadContext.put(KEY_USER, auth.getName() != null ? auth.getName() : ANONYMOUS);
        }
    }

    @Before(value = "execution(* com.epam.pipeline.security.saml.SAMLUserDetailsServiceImpl.loadUserBySAML(..))" +
            "&& args(credential,..)")
    public void addUserInfoFromSAML(JoinPoint joinPoint, SAMLCredential credential) {
        if (credential != null) {
            ThreadContext.put(KEY_USER, credential.getNameID().getValue().toUpperCase());
        }
    }

    @After(value = CHANGING_PERMISSION_POINTCUT +
            "|| execution(* com.epam.pipeline.security.saml.SAMLUserDetailsServiceImpl.loadUserBySAML(..)) " +
            "|| execution(* com.epam.pipeline.security.saml.SAMLProxyAuthenticationProvider.authenticate(..))")
    public void clearUserInfo() {
        ThreadContext.clearAll();
    }

}

