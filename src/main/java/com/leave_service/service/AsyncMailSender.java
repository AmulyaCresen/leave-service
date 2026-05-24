package com.leave_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncMailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.sender}")
    private String senderEmail;

    @Async("mailExecutor")
    public void send(String to, String subject, String html) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true);
            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("[AsyncMail] Sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("[AsyncMail] Failed to send to {}: {}", to, e.getMessage());
        }
    }
}
