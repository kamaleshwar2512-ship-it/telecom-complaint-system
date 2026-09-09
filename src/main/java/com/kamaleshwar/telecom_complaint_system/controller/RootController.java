package com.kamaleshwar.telecom_complaint_system.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sends "/" to the right place: the login page, or the dashboard for the user's role. */
@Controller
public class RootController {

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "ROLE_CUSTOMER" -> {
                    return "redirect:/customer/dashboard";
                }
                case "ROLE_AGENT" -> {
                    return "redirect:/agent/dashboard";
                }
                case "ROLE_ADMIN" -> {
                    return "redirect:/admin/dashboard";
                }
                default -> { /* keep looking */ }
            }
        }
        return "redirect:/login";
    }
}
