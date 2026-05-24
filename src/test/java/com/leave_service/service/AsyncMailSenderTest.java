package com.leave_service.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncMailSenderTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private AsyncMailSender asyncMailSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(asyncMailSender, "senderEmail", "sender@test.com");
    }

    @Test
    void send_success() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        asyncMailSender.send("to@test.com", "Test Subject", "<html>Test</html>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_failure() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail error"));

        asyncMailSender.send("to@test.com", "Test Subject", "<html>Test</html>");

        verify(mailSender).createMimeMessage();
    }
}
