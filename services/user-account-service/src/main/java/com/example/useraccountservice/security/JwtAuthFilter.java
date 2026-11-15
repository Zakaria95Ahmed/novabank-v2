package com.example.useraccountservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }


        String token = authHeader.substring(7);
        String userEmail;

        try{
            userEmail = jwtService.extractUsername(token);

        }catch (Exception e){
                log.error("Error extracting JWT TOken: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }


        if (userEmail !=null){
            UserDetails userDetails;
            try {
                userDetails = customUserDetailsService.loadUserByUsername(userEmail);
            } catch (UsernameNotFoundException e) {
                // SEC-07: a valid token for a user that no longer exists must not cause a 500.
                log.warn("JWT subject does not exist anymore; continuing unauthenticated");
                filterChain.doFilter(request, response);
                return;
            }

            // SEC-07: a disabled user must not stay authenticated just because the token is still valid.
            if(userDetails.isEnabled() && jwtService.isTokenValid(token, userDetails.getUsername())){
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);

    }
}
