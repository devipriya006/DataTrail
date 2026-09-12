package com.datatrail.backend.controller;

import com.datatrail.backend.entity.User;
import com.datatrail.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User createUser(@RequestBody Map<String, Object> request) {

        User user = new User(
                (String) request.get("username"),
                (String) request.get("email"),
                (String) request.get("passwordHash"),
                null
        );

        Long roleId = Long.valueOf(request.get("roleId").toString());

        return userService.createUser(user, roleId);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}