package com.leave_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.*;

class LeaveServiceApplicationTest {

    @Test
    void main_startsApplication() {
        try (var mockStatic = mockStatic(SpringApplication.class)) {
            LeaveServiceApplication.main(new String[]{});
            mockStatic.verify(() -> SpringApplication.run(LeaveServiceApplication.class, new String[]{}));
        }
    }
}
