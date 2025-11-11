package com.example.TradeShift.web.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

	public static class RegisterRequest {
		@NotBlank(message = "Username is required")
		public String username;
		public String email;
		public String phoneNumber;
	}
	

	public static class CompleteRegistrationRequest {
		@NotBlank(message = "Identifier is required")
		public String identifier;
		@NotBlank(message = "Password is required")
		public String password;
	}

	public static class LoginRequest {
		@NotBlank
		public String username;
		@NotBlank
		public String password;
	}

	public static class AuthResponse {
		public String token;
		public String username;
		public AuthResponse(String token, String username) {
			this.token = token;
			this.username = username;
		}
	}
}



