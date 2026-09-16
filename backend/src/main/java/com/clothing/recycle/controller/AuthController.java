package com.clothing.recycle.controller;

import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.User;
import com.clothing.recycle.repo.UserRepo;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepo userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ViewMapper vm;
    private final CurrentUser currentUser;

    public AuthController(UserRepo userRepo, PasswordEncoder encoder, JwtService jwt,
                          ViewMapper vm, CurrentUser currentUser) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.vm = vm;
        this.currentUser = currentUser;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.isEnabled() || !encoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwt.generate(user.getId(), user.getUsername(), user.getRole());
        return Map.of("token", token, "user", vm.user(user));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return vm.user(currentUser.get());
    }
}
