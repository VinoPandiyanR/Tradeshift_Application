package com.example.TradeShift.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		Map<String, String> response = new HashMap<>();
		response.put("error", "Validation failed");
		response.put("message", errors.toString());
		logger.warn("Validation error: {}", errors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
		logger.warn("Illegal argument: {}", ex.getMessage());
		
		Map<String, String> error = new HashMap<>();
		error.put("error", "Invalid request");
		

		String errorMessage = ex.getMessage();
		if (errorMessage == null || errorMessage.isEmpty()) {
			errorMessage = "Invalid request";
		}
		
		error.put("message", errorMessage);
		
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
		logger.error("Internal server error", ex);
		
		Map<String, String> error = new HashMap<>();
		error.put("error", "Internal server error");
		

		String errorMessage = ex.getMessage();
		if (errorMessage == null || errorMessage.isEmpty()) {
			errorMessage = ex.getClass().getSimpleName();
		}
		
		error.put("message", "An error occurred: " + errorMessage);
		

		error.put("exception", ex.getClass().getSimpleName());
		
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
	}
}

