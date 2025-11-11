package com.example.TradeShift.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    
    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    
    @Autowired
    private EmailService emailService;
    
    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private static final int OTP_LENGTH = 6;
    private static final int OTP_MAX_VALUE = 1000000;
    private static final long OTP_EXPIRY_TIME = 10 * 60 * 1000; // 10 minutes
    private final Random random = new Random();
    
    private static class OtpData {
        private final String otp;
        private final long expiryTime;
        private final Map<String, Object> registrationData;
        
        public OtpData(String otp, Map<String, Object> registrationData) {
            this.otp = otp;
            this.expiryTime = System.currentTimeMillis() + OTP_EXPIRY_TIME;
            this.registrationData = registrationData;
        }
        
        public String getOtp() {
            return otp;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        public Map<String, Object> getRegistrationData() {
            return registrationData;
        }
        
        public long getRemainingTime() {
            long remaining = expiryTime - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
    }
    

    public String generateOtp(String email, Map<String, Object> registrationData) {
        String otp = String.format("%0" + OTP_LENGTH + "d", random.nextInt(OTP_MAX_VALUE));

        otpStore.put(email, new OtpData(otp, registrationData));

        cleanupExpiredOtps();

        logger.info("Sending OTP to user's registered email: {}", email);
        boolean emailSent = emailService.sendOtpEmail(email, otp);
        
        if (!emailSent) {
            logger.error("Failed to send OTP email to user's registered email: {}. Please check email configuration.", email);
        } else {
            logger.info("OTP successfully sent to user's registered email: {}", email);
        }
        
        return otp;
    }


    public VerificationResult verifyOtp(String email, String otp) {
        if (otp == null || otp.trim().isEmpty() || !otp.matches("\\d{" + OTP_LENGTH + "}")) {
            return new VerificationResult(false, "INVALID_FORMAT", "OTP must be exactly " + OTP_LENGTH + " digits");
        }
        
        OtpData otpData = otpStore.get(email);
        
        if (otpData == null) {
            return new VerificationResult(false, "NOT_FOUND", "OTP not found. Please request a new OTP.");
        }
        
        if (otpData.isExpired()) {
            otpStore.remove(email);
            return new VerificationResult(false, "EXPIRED", "OTP has expired. Please request a new OTP.");
        }
        
        if (!otpData.getOtp().equals(otp.trim())) {
            return new VerificationResult(false, "INVALID", "Invalid OTP. Please try again.");
        }
        

        Map<String, Object> registrationData = new HashMap<>(otpData.getRegistrationData());
        
        return new VerificationResult(true, "SUCCESS", "OTP verified successfully.", registrationData);
    }
    

    public void removeRegistrationData(String email) {
        otpStore.remove(email);
    }
    

    public Map<String, Object> getRegistrationData(String email) {
        OtpData otpData = otpStore.get(email);
        if (otpData != null && !otpData.isExpired()) {
            return new HashMap<>(otpData.getRegistrationData());
        }
        return null;
    }
    

    public long getOtpRemainingTime(String email) {
        OtpData otpData = otpStore.get(email);
        if (otpData != null && !otpData.isExpired()) {
            return otpData.getRemainingTime();
        }
        return 0;
    }
    

    private void cleanupExpiredOtps() {
        otpStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    

    public static class VerificationResult {
        private final boolean success;
        private final String code;
        private final String message;
        private final Map<String, Object> registrationData;
        
        public VerificationResult(boolean success, String code, String message) {
            this(success, code, message, null);
        }
        
        public VerificationResult(boolean success, String code, String message, Map<String, Object> registrationData) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.registrationData = registrationData;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Map<String, Object> getRegistrationData() {
            return registrationData;
        }
    }
}

