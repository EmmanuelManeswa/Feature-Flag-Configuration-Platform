package com.featureflagplatform.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless bearer-token authentication: no cookies, no server-side session.
 * A missing or invalid token simply leaves the request unauthenticated —
 * whether that's acceptable is decided downstream by
 * {@code SecurityConfig}'s authorization rules and each endpoint's
 * {@code @PreAuthorize}, not by this filter.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.parse(token).ifPresent(claims -> authenticate(claims, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String email = claims.get("email", String.class);
        try {
            var userDetails = userDetailsService.loadUserByUsername(email);
            // A fresh DB lookup on every request (not a claim decoded from the
            // token itself), specifically so this check reflects the account's
            // *current* state — an admin disabling a user takes effect on that
            // user's very next request, not just at their next login. This
            // path builds the Authentication directly rather than going
            // through AuthenticationManager/DaoAuthenticationProvider, so it
            // doesn't get that provider's own isEnabled() check for free; it
            // has to be done here instead.
            if (!userDetails.isEnabled()) {
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException e) {
            // Token was validly signed but the user no longer exists (deleted
            // after the token was issued) — leave the request unauthenticated
            // rather than failing the filter chain.
        }
    }
}
