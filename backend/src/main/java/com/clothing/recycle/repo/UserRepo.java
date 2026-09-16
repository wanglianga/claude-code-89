package com.clothing.recycle.repo;

import com.clothing.recycle.model.User;
import com.clothing.recycle.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(Role role);
    List<User> findByRoleAndCommunityName(Role role, String communityName);
}
