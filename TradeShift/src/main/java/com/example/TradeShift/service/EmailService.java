package com.example.TradeShift.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.from:your_email@example.com}")
    private String fromEmail;


    public boolean sendOtpEmail(String toEmail, String otp) {
        if (javaMailSender == null) {
            logger.error("JavaMailSender is not configured. Please configure email settings in application.properties");
            logger.error("To configure Gmail:");
            logger.error("1. Go to https://myaccount.google.com/apppasswords");
            logger.error("2. Generate a 16-character App Password");
            logger.error("3. Update application.properties with your email and app password");
            return false;
        }

        if (toEmail == null || toEmail.trim().isEmpty()) {
            logger.error("Cannot send OTP email: recipient email is empty");
            return false;
        }

        if (otp == null || otp.trim().isEmpty()) {
            logger.error("Cannot send OTP email: OTP is empty");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your OTP for TradeShift Registration");
            message.setText("Your One-Time Password (OTP) is: " + otp + "\n\nThis OTP is valid for 10 minutes.\n\nDo not share this OTP with anyone.");
            
            javaMailSender.send(message);
            logger.info("✅ OTP email sent successfully to user's registered email: {}", toEmail);
            return true;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMessage.contains("authentication") || errorMessage.contains("authentication failed") 
                || e.getClass().getSimpleName().contains("Authentication")) {
                logger.error("❌ Email authentication failed. Please check your email credentials in application.properties");
                logger.error("For Gmail, you MUST use an App Password, not your regular password.");
                logger.error("Steps to fix:");
                logger.error("1. Go to: https://myaccount.google.com/apppasswords");
                logger.error("2. Select 'Mail' and 'Other (Custom name)'");
                logger.error("3. Enter 'TradeShift' as the app name");
                logger.error("4. Click 'Generate' and copy the 16-character password");
                logger.error("5. Update application.properties: spring.mail.password=<your-16-char-app-password>");
                logger.error("6. Also update: spring.mail.username=<your-email@gmail.com>");
                logger.error("7. Also update: spring.mail.from=<your-email@gmail.com>");
                return false;
            }
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            logger.error("Error type: {}", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                logger.error("Cause: {}", e.getCause().getMessage());
            }
            return false;
        }
    }
}

