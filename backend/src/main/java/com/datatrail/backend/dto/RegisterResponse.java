package com.datatrail.backend.dto;

public class RegisterResponse {

    private Long userId;
    private String username;
    private String email;
    private String role;

    public RegisterResponse(
            Long userId,
            String username,
            String email,
            String role) {

        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}