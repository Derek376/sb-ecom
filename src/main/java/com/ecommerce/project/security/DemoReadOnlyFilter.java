package com.ecommerce.project.security;

import com.ecommerce.project.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Installed only in the security chain, after JWT authentication.
public class DemoReadOnlyFilter extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> SESSION_ENDPOINTS = Set.of(
            "/api/auth/signin", "/api/auth/signout", "/api/auth/demo-login");
    private final UserRepository users;

    public DemoReadOnlyFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        boolean sessionRequest = "POST".equals(request.getMethod()) && SESSION_ENDPOINTS.contains(path);
        if (!READ_METHODS.contains(request.getMethod()) && !sessionRequest
                && authentication != null && authentication.isAuthenticated()
                && users.findByUserName(authentication.getName())
                .map(user -> Boolean.TRUE.equals(user.getDemoAccount())).orElse(false)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"DEMO_READ_ONLY\",\"message\":\"Preview only in demo mode. Changes, orders and payments are disabled.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
