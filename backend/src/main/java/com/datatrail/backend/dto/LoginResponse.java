package com.datatrail.backend.dto;

public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String username;
    private String role;

    public LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String username,
            String role) {

        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.username = username;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}