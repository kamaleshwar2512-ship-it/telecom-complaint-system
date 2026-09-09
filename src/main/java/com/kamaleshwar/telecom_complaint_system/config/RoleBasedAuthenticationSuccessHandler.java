package com.kamaleshwar.telecom_complaint_system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** After a successful login, sends each role to its own dashboard instead of one shared landing page. */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        String target = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(this::dashboardFor)
                .orElse("/login");
        response.sendRedirect(request.getContextPath() + target);
    }

    private String dashboardFor(String authority) {
        return switch (authority) {
            case "ROLE_CUSTOMER" -> "/customer/dashboard";
            case "ROLE_AGENT" -> "/agent/dashboard";
            case "ROLE_ADMIN" -> "/admin/dashboard";
            default -> "/login";
        };
    }
}
