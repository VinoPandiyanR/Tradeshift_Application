package com.example.TradeShift.web;

import com.example.TradeShift.model.User;
import com.example.TradeShift.repository.UserRepository;
import com.example.TradeShift.security.JwtService;
import com.example.TradeShift.web.dto.AuthDtos.LoginRequest;
import com.example.TradeShift.web.dto.AuthDtos.AuthResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
	
	private final AuthenticationManager authenticationManager;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final com.example.TradeShift.service.OtpService otpService;

	public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, com.example.TradeShift.service.OtpService otpService) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.otpService = otpService;
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
		try {
			String username = request.get("username");
			String email = request.get("email");
			String phoneNumber = request.get("phoneNumber");

			if (username == null || username.trim().isEmpty()) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Username is required");
				return ResponseEntity.badRequest().body(error);
			}
			
			boolean hasEmail = email != null && !email.trim().isEmpty();
			boolean hasPhoneNumber = phoneNumber != null && !phoneNumber.trim().isEmpty();

			if (!hasEmail) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Email is required");
				return ResponseEntity.badRequest().body(error);
			}
			
			if (!hasPhoneNumber) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Phone number is required");
				return ResponseEntity.badRequest().body(error);
			}

			if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Please enter a valid email address");
				return ResponseEntity.badRequest().body(error);
			}

			String phone = phoneNumber.trim();
			if (!phone.startsWith("+")) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Phone number must include country code with + (e.g., +919876543210 for India, +1234567890 for US)");
				return ResponseEntity.badRequest().body(error);
			}
			if (!phone.matches("^\\+\\d{10,15}$")) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Invalid phone number format. Use +countrycode format (e.g., +919876543210)");
				return ResponseEntity.badRequest().body(error);
			}

			if (userRepository.existsByUsername(username)) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Username already exists");
				error.put("message", "Username already exists");
				return ResponseEntity.badRequest().body(error);
			}

			if (userRepository.existsByEmail(email)) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Email already exists");
				error.put("message", "Email already exists");
				return ResponseEntity.badRequest().body(error);
			}

			if (userRepository.existsByPhoneNumber(phoneNumber)) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Phone number already exists");
				error.put("message", "Phone number already exists");
				return ResponseEntity.badRequest().body(error);
			}

			Map<String, Object> registrationData = new HashMap<>();
			registrationData.put("username", username);
			registrationData.put("email", email);
			registrationData.put("phoneNumber", phoneNumber);

			logger.info("Generating OTP for user registration. Email: {}", email);
			otpService.generateOtp(email, registrationData);
			
			logger.info("OTP generated and sent to user's registered email: {}", email);
			logger.info("Each user receives OTP at their own registered email address: {}", email);
			
			Map<String, Object> response = new HashMap<>();
			response.put("message", "OTP sent successfully to your email. Please verify OTP to complete registration.");
			response.put("email", email);
			response.put("note", "If you don't receive email, check application logs for OTP (email may not be configured)");

			long remainingTime = otpService.getOtpRemainingTime(email);
			if (remainingTime > 0) {
				response.put("expiresIn", remainingTime);
				response.put("expiresInSeconds", remainingTime / 1000);
			}
			
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "An error occurred during registration: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			error.put("exception", e.getClass().getSimpleName());
			logger.error("Error during registration", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}
	
	@PostMapping("/verify-otp")
	public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
		try {
			String email = request.get("email");
			String otp = request.get("otp");
			
			if (email == null || email.trim().isEmpty()) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Email is required");
				return ResponseEntity.badRequest().body(error);
			}
			
			if (otp == null || otp.trim().isEmpty()) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "OTP is required");
				return ResponseEntity.badRequest().body(error);
			}

			com.example.TradeShift.service.OtpService.VerificationResult result = otpService.verifyOtp(email, otp);
			
			if (!result.isSuccess()) {
				Map<String, Object> error = new HashMap<>();
				error.put("error", result.getCode());
				error.put("message", result.getMessage());
				error.put("code", result.getCode());
				return ResponseEntity.badRequest().body(error);
			}

			Map<String, Object> response = new HashMap<>();
			response.put("message", "OTP verified successfully. Please set your password to complete registration.");
			response.put("verified", true);
			response.put("code", result.getCode());
			response.put("email", email);
			
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "An error occurred during OTP verification: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			error.put("exception", e.getClass().getSimpleName());
			logger.error("Error during OTP verification", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}
	
	@PostMapping("/complete-registration")
	public ResponseEntity<?> completeRegistration(@RequestBody Map<String, String> request) {
		try {
			String email = request.get("email");
			String password = request.get("password");
			
			if (email == null || email.trim().isEmpty()) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Email is required");
				return ResponseEntity.badRequest().body(error);
			}
			
			if (password == null || password.trim().isEmpty() || password.length() < 6) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Password is required and must be at least 6 characters long");
				return ResponseEntity.badRequest().body(error);
			}

			Map<String, Object> registrationData = otpService.getRegistrationData(email);
			
			if (registrationData == null) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Session expired");
				error.put("message", "OTP verification session expired. Please start registration again.");
				return ResponseEntity.badRequest().body(error);
			}

			String username = (String) registrationData.get("username");
			String userEmail = (String) registrationData.get("email");
			String phoneNumber = (String) registrationData.get("phoneNumber");

			if (userRepository.existsByUsername(username)) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Username already exists");
				error.put("message", "Username already exists");
				return ResponseEntity.badRequest().body(error);
			}

			User user = new User();
			user.setUsername(username);
			user.setEmail(userEmail);
			user.setPhoneNumber(phoneNumber);
			user.setPassword(passwordEncoder.encode(password));
			user.setRole("USER");
			userRepository.save(user);

			otpService.removeRegistrationData(email);

			Map<String, Object> claims = new HashMap<>();
			String token = jwtService.generateToken(claims, user.getUsername());
			
			Map<String, Object> response = new HashMap<>();
			response.put("message", "Registration completed successfully!");
			response.put("token", token);
			response.put("username", user.getUsername());
			
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "An error occurred during registration completion: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			error.put("exception", e.getClass().getSimpleName());
			logger.error("Error during registration completion", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
		try {
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
					request.username, request.password);
			authenticationManager.authenticate(authToken);
			
			Map<String, Object> claims = new HashMap<>();
			String token = jwtService.generateToken(claims, request.username);
			return ResponseEntity.ok(new AuthResponse(token, request.username));
		} catch (BadCredentialsException e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Invalid username or password");
			error.put("message", "Invalid username or password");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
		} catch (AuthenticationException e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Authentication failed");
			error.put("message", "Authentication failed: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "An error occurred during login: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			error.put("exception", e.getClass().getSimpleName());
			logger.error("Error during login", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}

	@GetMapping("/profile")
	public ResponseEntity<?> getProfile(org.springframework.security.core.Authentication auth) {
		try {
			String username = auth.getName();
			User user = userRepository.findByUsername(username)
					.orElseThrow(() -> new RuntimeException("User not found"));
			
			Map<String, Object> profile = new HashMap<>();
			profile.put("username", user.getUsername());
			profile.put("email", user.getEmail());
			profile.put("phoneNumber", user.getPhoneNumber());
			profile.put("role", user.getRole());
			
			return ResponseEntity.ok(profile);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "Failed to retrieve profile: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			logger.error("Error retrieving profile", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}

	@PutMapping("/profile")
	public ResponseEntity<?> updateProfile(org.springframework.security.core.Authentication auth, @RequestBody Map<String, String> request) {
		try {
			String username = auth.getName();
			User user = userRepository.findByUsername(username)
					.orElseThrow(() -> new RuntimeException("User not found"));

			if (request.containsKey("email")) {
				String newEmail = request.get("email");
				if (newEmail != null && !newEmail.trim().isEmpty()) {
					if (userRepository.existsByEmail(newEmail) && !newEmail.equals(user.getEmail())) {
						Map<String, String> error = new HashMap<>();
						error.put("error", "Email already exists");
						error.put("message", "Email already exists");
						return ResponseEntity.badRequest().body(error);
					}
					user.setEmail(newEmail.trim());
				} else {
					user.setEmail(null);
				}
			}

			if (request.containsKey("phoneNumber")) {
				String newPhoneNumber = request.get("phoneNumber");
				if (newPhoneNumber != null && !newPhoneNumber.trim().isEmpty()) {
					if (userRepository.existsByPhoneNumber(newPhoneNumber) && !newPhoneNumber.equals(user.getPhoneNumber())) {
						Map<String, String> error = new HashMap<>();
						error.put("error", "Phone number already exists");
						error.put("message", "Phone number already exists");
						return ResponseEntity.badRequest().body(error);
					}
					user.setPhoneNumber(newPhoneNumber.trim());
				} else {
					user.setPhoneNumber(null);
				}
			}

			if (user.getEmail() == null && user.getPhoneNumber() == null) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Validation error");
				error.put("message", "Either email or phone number must be provided");
				return ResponseEntity.badRequest().body(error);
			}
			
			userRepository.save(user);
			
			Map<String, Object> profile = new HashMap<>();
			profile.put("username", user.getUsername());
			profile.put("email", user.getEmail());
			profile.put("phoneNumber", user.getPhoneNumber());
			profile.put("role", user.getRole());
			
			return ResponseEntity.ok(profile);
		} catch (Exception e) {
			Map<String, String> error = new HashMap<>();
			error.put("error", "Internal server error");
			error.put("message", "Failed to update profile: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			logger.error("Error updating profile", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}
}



