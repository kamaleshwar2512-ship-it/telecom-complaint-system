package com.kamaleshwar.telecom_complaint_system.dto;

import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Form backing bean for admins creating/editing an SLA rule. */
@Getter
@Setter
public class SlaRuleForm {

    private Long id;

    @NotNull(message = "Please select a category")
    private ComplaintCategory category;

    @NotNull(message = "Please select a priority")
    private Priority priority;

    @NotNull(message = "Resolution hours is required")
    @Min(value = 1, message = "Resolution hours must be at least 1")
    @Max(value = 8760, message = "Resolution hours must be at most 8760 (one year)")
    private Integer resolutionHours;
}
