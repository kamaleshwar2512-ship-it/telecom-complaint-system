package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.RegistrationForm;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Registration and lookup of users. Authentication itself is Spring Security's job (see config package). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Registers a new CUSTOMER account. Public self-registration is only ever for customers -
     * agent and admin accounts are created by demo/seed data or by an administrator. */
    @Transactional
    public User registerCustomer(RegistrationForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        User user = new User(form.getName(), form.getEmail(),
                passwordEncoder.encode(form.getPassword()), Role.CUSTOMER, form.getPhone());
        return userRepository.save(user);
    }

    public List<User> findAgents() {
        return userRepository.findByRoleOrderByNameAsc(Role.AGENT);
    }

    /** Looks up the currently logged-in user by the email Spring Security authenticated them with. */
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
