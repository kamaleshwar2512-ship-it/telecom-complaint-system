package com.kamaleshwar.telecom_complaint_system.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Catches exceptions that individual controllers did not handle themselves and renders the
 * shared error page instead of leaking a stack trace to the user.
 *
 * <p>Most expected business errors (invalid status transition, missing SLA rule, invalid
 * assignment) are caught directly inside the relevant controller method so the user is sent
 * back to the form with a friendly message. This class is the safety net for everything else,
 * plus the two access-control exceptions which are simplest to handle in one place.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ComplaintNotFoundException.class)
    public ModelAndView handleNotFound(ComplaintNotFoundException ex) {
        return errorView(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedComplaintAccessException.class)
    public ModelAndView handleUnauthorized(UnauthorizedComplaintAccessException ex) {
        return errorView(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, SlaRuleNotFoundException.class, InvalidAssignmentException.class})
    public ModelAndView handleBusinessRuleViolation(RuntimeException ex) {
        return errorView(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private ModelAndView errorView(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        return mav;
    }
}
