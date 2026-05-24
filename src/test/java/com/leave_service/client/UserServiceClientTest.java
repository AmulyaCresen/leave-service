package com.leave_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock private RestTemplate restTemplate;
    @InjectMocks private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userServiceClient, "userServiceUrl", "http://localhost:8080");
    }

    @Test
    void getFullName_success() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("John Doe");

        String result = userServiceClient.getFullName("john@test.com");

        assertEquals("John Doe", result);
    }

    @Test
    void getFullName_returnsEmailOnNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(null);

        String result = userServiceClient.getFullName("john@test.com");

        assertEquals("john@test.com", result);
    }

    @Test
    void getFullName_returnsEmailOnBlank() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("   ");

        String result = userServiceClient.getFullName("john@test.com");

        assertEquals("john@test.com", result);
    }

    @Test
    void getFullName_returnsEmailOnException() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("Service down"));

        String result = userServiceClient.getFullName("john@test.com");

        assertEquals("john@test.com", result);
    }
}
