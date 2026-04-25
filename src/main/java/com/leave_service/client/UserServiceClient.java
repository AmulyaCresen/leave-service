package com.leave_service.client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
@Slf4j
@Component
public class UserServiceClient {
    private final RestTemplate restTemplate;
    @Value("${app.user-service.url}")
    private String userServiceUrl;
    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    public String getFullName(String email) {
        try {
            String fullName = restTemplate.getForObject(
                userServiceUrl + "/users/fullname?email=" + email, String.class);
            return (fullName != null && !fullName.isBlank()) ? fullName : email;
        } catch (Exception e) {
            log.warn("[UserServiceClient] Could not fetch full name for {}: {}", email, e.getMessage());
            return email;
        }
    }
}