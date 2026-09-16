package com.clothing.recycle.security;

import com.clothing.recycle.model.Role;
import com.clothing.recycle.model.User;
import com.clothing.recycle.repo.UserRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** 从安全上下文取当前登录用户，并提供角色断言 */
@Component
public class CurrentUser {

    private final UserRepo userRepo;

    public CurrentUser(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    public String username() {
        return (String) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
    }

    public User get() {
        Optional<User> u = userRepo.findByUsername(username());
        return u.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
    }

    public boolean hasRole(Role... roles) {
        Role mine = get().getRole();
        for (Role r : roles) {
            if (mine == r) return true;
        }
        return false;
    }

    public void require(Role... roles) {
        if (!hasRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权操作");
        }
    }
}
