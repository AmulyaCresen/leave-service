package com.leave_service.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsDeduplicationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, new DeduplicatingResponseWrapper((HttpServletResponse) response));
    }

    private static class DeduplicatingResponseWrapper extends HttpServletResponseWrapper {

        private final Set<String> writtenCorsHeaders = new HashSet<>();

        DeduplicatingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (isCorsHeader(name) && !writtenCorsHeaders.add(name.toLowerCase())) {
                return; // duplicate — skip
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isCorsHeader(name)) {
                if (!writtenCorsHeaders.add(name.toLowerCase())) {
                    return; // duplicate — skip
                }
                super.setHeader(name, value); // use setHeader so value is not appended
            } else {
                super.addHeader(name, value);
            }
        }

        private boolean isCorsHeader(String name) {
            return name != null && name.toLowerCase().startsWith("access-control-");
        }
    }
}
