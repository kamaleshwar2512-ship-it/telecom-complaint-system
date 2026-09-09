package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.ComplaintForm;
import com.kamaleshwar.telecom_complaint_system.dto.RegistrationForm;
import com.kamaleshwar.telecom_complaint_system.dto.SlaRuleForm;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain Jakarta Bean Validation checks on the form objects - no Spring context needed. */
class ValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void registrationRejectsInvalidEmailAndShortPassword() {
        RegistrationForm form = new RegistrationForm();
        form.setName("Jane Doe");
        form.setEmail("not-an-email");
        form.setPassword("123");

        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(form);
        assertFalse(violations.isEmpty());
    }

    @Test
    void registrationAcceptsValidData() {
        RegistrationForm form = new RegistrationForm();
        form.setName("Jane Doe");
        form.setEmail("jane@example.com");
        form.setPassword("SecurePass123");
        form.setPhone("9876543210");

        assertTrue(validator.validate(form).isEmpty());
    }

    @Test
    void complaintFormRejectsBlankDescriptionAndMissingCategory() {
        ComplaintForm form = new ComplaintForm();
        form.setDescription("");

        Set<ConstraintViolation<ComplaintForm>> violations = validator.validate(form);
        assertTrue(violations.size() >= 2); // category missing, priority missing, description too short
    }

    @Test
    void slaRuleFormRejectsZeroAndNegativeResolutionHours() {
        SlaRuleForm zero = new SlaRuleForm();
        zero.setCategory(ComplaintCategory.NETWORK);
        zero.setPriority(Priority.HIGH);
        zero.setResolutionHours(0);
        assertFalse(validator.validate(zero).isEmpty());

        SlaRuleForm negative = new SlaRuleForm();
        negative.setCategory(ComplaintCategory.NETWORK);
        negative.setPriority(Priority.HIGH);
        negative.setResolutionHours(-5);
        assertFalse(validator.validate(negative).isEmpty());
    }

    @Test
    void slaRuleFormAcceptsPositiveResolutionHours() {
        SlaRuleForm form = new SlaRuleForm();
        form.setCategory(ComplaintCategory.NETWORK);
        form.setPriority(Priority.HIGH);
        form.setResolutionHours(24);
        assertTrue(validator.validate(form).isEmpty());
    }
}
