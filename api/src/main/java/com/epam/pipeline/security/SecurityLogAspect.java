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

import com.auth0.jwt.JWT;
import com.epam.pipeline.security.saml.SAMLProxyAuthentication;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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

    public static final String PERMISSION_RELATED_METHODS_POINTCUT =
            "execution(* com.epam.pipeline.manager.security.GrantPermissionManager.*(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.deletePermissions(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.deleteAllPermissions(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.lockEntity(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.unlockEntity(..))" +
            "|| execution(* com.epam.pipeline.manager.security.GrantPermissionManager.changeOwner(..))";

    public static final String USER_RELATED_METHODS_POINTCUT =
            "execution(* com.epam.pipeline.manager.user.UserManager.updateUser(..)) " +
            "|| execution(* com.epam.pipeline.manager.user.UserManager.updateUserBlockingStatus(..))" +
            "|| execution(* com.epam.pipeline.manager.user.UserManager.updateUserSAMLInfo(..))" +
            "|| execution(* com.epam.pipeline.manager.user.UserManager.delete*(..))" +
            "|| execution(* com.epam.pipeline.manager.user.UserManager.create*(..))" +
            "|| execution(* com.epam.pipeline.manager.user.RoleManager.assignRole(..))" +
            "|| execution(* com.epam.pipeline.manager.user.RoleManager.removeRole(..))";


    public static final String ANONYMOUS = "Anonymous";
    public static final String KEY_USER = "user";
    public static final String AUTH_TYPE = "auth_type";
    public static final String JWT = "JWT";
    public static final String SAML = "SAML";
    public static final String POST = "POST";


    @Before(value = PERMISSION_RELATED_METHODS_POINTCUT + " || " + USER_RELATED_METHODS_POINTCUT)
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
        ThreadContext.put(AUTH_TYPE, SAML);
    }

    @Before(value = "execution(* com.epam.pipeline.security.saml.SAMLUserDetailsServiceImpl.loadUserBySAML(..))" +
            "&& args(credential,..)")
    public void addUserInfoFromSAML(JoinPoint joinPoint, SAMLCredential credential) {
        if (credential != null) {
            ThreadContext.put(KEY_USER, credential.getNameID().getValue().toUpperCase());
        }
        ThreadContext.put(AUTH_TYPE, SAML);
    }

    @Before(value = "execution(* com.epam.pipeline.security.jwt.JwtTokenVerifier.readClaims(..)) && args(token,..)")
    public void addUserInfoWhileAuthByJWT(JoinPoint joinPoint, String token) {
        JWT decode = com.auth0.jwt.JWT.decode(token);
        ThreadContext.put(AUTH_TYPE, JWT);
        ThreadContext.put(KEY_USER, decode.getSubject() != null ? decode.getSubject() : ANONYMOUS);
    }

    @After(value = PERMISSION_RELATED_METHODS_POINTCUT +
            "|| execution(* com.epam.pipeline.security.saml.SAMLUserDetailsServiceImpl.loadUserBySAML(..)) " +
            "|| execution(* com.epam.pipeline.security.saml.SAMLProxyAuthenticationProvider.authenticate(..))")
    public void clearUserInfo() {
        ThreadContext.clearAll();
    }

}

