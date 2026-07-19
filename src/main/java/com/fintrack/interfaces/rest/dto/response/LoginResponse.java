package com.fintrack.interfaces.rest.dto.response;

/**
 * LoginResponse — DTO de saída para autenticação, contendo o token JWT.
 */
public record LoginResponse(
    String token,
    String type,
    long expiresIn
) {
    public LoginResponse(String token, long expiresIn) {
        this(token, "Bearer", expiresIn);
    }
}
