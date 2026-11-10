package com.kamaleshwar.telecom_complaint_system.security;

import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 moved this annotation out of spring-boot-test-autoconfigure into spring-boot-webmvc-test.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the role-based URL rules from SecurityConfig actually hold: each role can reach its
 * own area, nobody can reach another role's area, and anonymous requests get sent to the login page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // roll back the test user so it cannot clash with other test classes sharing the H2 database
class AccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static final String CUSTOMER_EMAIL = "access-control-customer@test.com";

    /**
     * CustomerController looks the logged-in user up in the database by email. @WithMockUser only
     * fakes the security context, so without a matching row the lookup throws and the page returns 500.
     */
    @BeforeEach
    void createCustomerAccount() {
        if (userRepository.findByEmail(CUSTOMER_EMAIL).isEmpty()) {
            userRepository.save(new User("Test Customer", CUSTOMER_EMAIL, "not-used", Role.CUSTOMER, "9999999999"));
        }
    }

    @Test
    void unauthenticatedUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotAccessAdminArea() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotAccessAgentArea() throws Exception {
        mockMvc.perform(get("/agent/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotAccessAdminArea() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerCanAccessOwnDashboard() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }
}
