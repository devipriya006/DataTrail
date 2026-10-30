package com.datatrail.backend.service;

import com.datatrail.backend.entity.Role;
import com.datatrail.backend.entity.User;
import com.datatrail.backend.repository.RoleRepository;
import com.datatrail.backend.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public User createUser(User user, Long roleId) {

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setRole(role);

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User updateUserRole(Long userId, String roleName) {

        String normalizedRole = roleName == null
                ? ""
                : roleName.trim().toUpperCase(Locale.ROOT);

        if (!Set.of("ADMIN", "USER").contains(normalizedRole)) {
            throw new IllegalArgumentException(
                    "Invalid role. Allowed roles: ADMIN, USER"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String currentRole = user.getRole().getRoleName();

        if ("ADMIN".equalsIgnoreCase(currentRole)
                && "USER".equals(normalizedRole)
                && userRepository.countByRole_RoleName("ADMIN") <= 1) {

            throw new IllegalStateException(
                    "Cannot remove the last administrator"
            );
        }

        Role role = roleRepository.findByRoleName(normalizedRole)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setRole(role);

        return userRepository.save(user);
    }
}