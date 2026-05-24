package com.leave_service.client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@Component
public class UserServiceClient {
    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, String> fullNameCache = new ConcurrentHashMap<>();
    @Value("${app.user-service.url}")
    private String userServiceUrl;
    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    public String getFullName(String email) {
        return fullNameCache.computeIfAbsent(email, e -> {
            try {
                String fullName = restTemplate.getForObject(
                    userServiceUrl + "/users/fullname?email=" + e, String.class);
                return (fullName != null && !fullName.isBlank()) ? fullName : e;
            } catch (Exception ex) {
                log.warn("[UserServiceClient] Could not fetch full name for {}: {}", e, ex.getMessage());
                return e;
            }
        });
    }
}
