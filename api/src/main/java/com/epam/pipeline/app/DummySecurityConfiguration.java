package com.epam.pipeline.app;

import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

//@Configuration
public class DummySecurityConfiguration {

    @Autowired
    private UserManager userManager;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .addFilterBefore((request, response, chain) -> {
                    SecurityContextHolder.getContext().setAuthentication(admin());
                    chain.doFilter(request, response);
                }, AbstractPreAuthenticatedProcessingFilter.class);

        return http.build();
    }

    private UsernamePasswordAuthenticationToken admin() {
        var user = PipelineUser.builder()
                .userName("PIPE_ADMIN")
                .id(1L)
                .admin(true)
                .roles(Collections.singletonList(new Role("ROLE_ADMIN")))
                .groups(Collections.emptyList())
                .build();
        var userContext = new UserContext(user);
        return new UsernamePasswordAuthenticationToken(
                userContext, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken user() {
        var user = userManager.loadUserByName("USER");
        if (Objects.isNull(user)) {
            var pipelineUserVO = new PipelineUserVO();
            pipelineUserVO.setUserName("USER");
            pipelineUserVO.setRoleIds(Collections.singletonList(2L));
            userManager.create(pipelineUserVO);
        }
        var userContext = new UserContext(user);
        return new UsernamePasswordAuthenticationToken(
                userContext, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
