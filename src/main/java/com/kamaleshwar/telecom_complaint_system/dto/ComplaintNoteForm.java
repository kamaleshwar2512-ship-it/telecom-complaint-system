package com.kamaleshwar.telecom_complaint_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Form backing bean for an agent adding a note to (or resolving) a complaint. */
@Getter
@Setter
public class ComplaintNoteForm {

    @NotBlank(message = "Please enter a note")
    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}
