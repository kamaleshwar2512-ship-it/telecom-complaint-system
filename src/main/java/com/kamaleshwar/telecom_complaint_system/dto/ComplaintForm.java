package com.kamaleshwar.telecom_complaint_system.dto;

import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Form backing bean for a customer raising a new complaint. */
@Getter
@Setter
public class ComplaintForm {

    @NotNull(message = "Please select a category")
    private ComplaintCategory category;

    @NotNull(message = "Please select a priority")
    private Priority priority;

    @NotBlank(message = "Please describe the problem")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;
}
