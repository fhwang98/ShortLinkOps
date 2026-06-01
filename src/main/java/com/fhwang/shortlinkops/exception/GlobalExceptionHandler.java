package com.fhwang.shortlinkops.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LinkNotFoundException.class)
    public Object handleLinkNotFound(LinkNotFoundException ex, HttpServletRequest request) {
        if (shouldReturnJson(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("LINK_NOT_FOUND", ex.getMessage()));
        }

        return errorView("error/404", HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ExpiredLinkException.class)
    public Object handleExpiredLink(ExpiredLinkException ex, HttpServletRequest request) {
        if (shouldReturnJson(request)) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(new ErrorResponse("LINK_EXPIRED", ex.getMessage()));
        }

        return errorView("error/410", HttpStatus.GONE, "LINK_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다.");

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);

        if (shouldReturnJson(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
        }

        return errorView("error/500", HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");
    }

    private boolean shouldReturnJson(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/")) {
            return true;
        }

        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private ModelAndView errorView(String viewName, HttpStatus status, String code, String message) {
        ModelAndView modelAndView = new ModelAndView(viewName);
        modelAndView.setStatus(status);
        modelAndView.addObject("code", code);
        modelAndView.addObject("message", message);
        return modelAndView;
    }
}
