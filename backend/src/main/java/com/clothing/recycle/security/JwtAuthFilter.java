package com.clothing.recycle.security;

import com.clothing.recycle.model.Role;
import com.clothing.recycle.model.User;
import com.clothing.recycle.repo.UserRepo;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepo userRepo;

    public JwtAuthFilter(JwtService jwt, UserRepo userRepo) {
        this.jwt = jwt;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwt.parse(header.substring(7));
                String username = claims.getSubject();
                Optional<User> user = userRepo.findByUsername(username);
                if (user.isPresent() && user.get().isEnabled()) {
                    User u = user.get();
                    var authority = new SimpleGrantedAuthority("ROLE_" + u.getRole().name());
                    var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // 无效令牌按未认证处理
            }
        }
        chain.doFilter(request, response);
    }
}
