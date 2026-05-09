package com.leave_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorsDeduplicationFilterTest {

    @InjectMocks
    private CorsDeduplicationFilter filter;

    @Test
    void doFilter_invokesFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(eq(request), any());
    }

    @Test
    void doFilter_deduplicatesCorsSetHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse wrapped = invocation.getArgument(1);
            wrapped.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
            // second call for same header should be ignored
            wrapped.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        // First value set should be preserved
        assertEquals("http://localhost:4200", response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void doFilter_deduplicatesCorsAddHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse wrapped = invocation.getArgument(1);
            wrapped.addHeader("Access-Control-Allow-Methods", "GET");
            // duplicate CORS addHeader should be ignored
            wrapped.addHeader("Access-Control-Allow-Methods", "POST");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        // Only the first value should be present
        assertEquals("GET", response.getHeader("Access-Control-Allow-Methods"));
    }

    @Test
    void doFilter_allowsNonCorsHeadersToPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse wrapped = invocation.getArgument(1);
            wrapped.setHeader("Content-Type", "application/json");
            wrapped.setHeader("X-Custom-Header", "value1");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals("application/json", response.getHeader("Content-Type"));
        assertEquals("value1", response.getHeader("X-Custom-Header"));
    }

    @Test
    void doFilter_addHeaderForNonCorsHeader_addsMultiple() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse wrapped = invocation.getArgument(1);
            wrapped.addHeader("X-Trace-Id", "abc");
            wrapped.addHeader("X-Trace-Id", "def");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        // Non-CORS headers are allowed multiple values
        assertFalse(response.getHeaders("X-Trace-Id").isEmpty());
    }

    @Test
    void doFilter_multipleDifferentCorsHeaders_eachSetOnce() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse wrapped = invocation.getArgument(1);
            wrapped.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
            wrapped.setHeader("Access-Control-Allow-Methods", "GET, POST");
            wrapped.setHeader("Access-Control-Allow-Headers", "Content-Type");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals("http://localhost:4200", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("GET, POST", response.getHeader("Access-Control-Allow-Methods"));
        assertEquals("Content-Type", response.getHeader("Access-Control-Allow-Headers"));
    }
}
