package com.azu.timetable.server.web;

import com.azu.timetable.server.core.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(ApiException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", exception.detail());
        return ResponseEntity.status(exception.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        java.util.logging.Logger.getLogger("ApiExceptionHandler")
                .log(java.util.logging.Level.SEVERE, "Unhandled exception", exception);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", "Internal Server Error");
        return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}