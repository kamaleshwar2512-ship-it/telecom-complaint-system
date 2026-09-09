package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.RegistrationForm;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationHashesThePasswordAndDefaultsToCustomerRole() {
        RegistrationForm form = new RegistrationForm();
        form.setName("Jane Doe");
        form.setEmail("jane.hash.test@example.com");
        form.setPassword("PlainTextPassword1");

        User saved = userService.registerCustomer(form);

        assertEquals(Role.CUSTOMER, saved.getRole());
        assertNotEquals("PlainTextPassword1", saved.getPassword(), "password must never be stored in plain text");
        assertTrue(passwordEncoder.matches("PlainTextPassword1", saved.getPassword()));
    }

    @Test
    void cannotRegisterTwoAccountsWithTheSameEmail() {
        RegistrationForm form = new RegistrationForm();
        form.setName("Jane Doe");
        form.setEmail("duplicate.test@example.com");
        form.setPassword("PlainTextPassword1");
        userService.registerCustomer(form);

        RegistrationForm duplicate = new RegistrationForm();
        duplicate.setName("Someone Else");
        duplicate.setEmail("duplicate.test@example.com");
        duplicate.setPassword("AnotherPassword1");

        assertThrows(IllegalArgumentException.class, () -> userService.registerCustomer(duplicate));
    }
}
