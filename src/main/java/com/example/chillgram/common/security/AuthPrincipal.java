package com.example.chillgram.common.security;

public record AuthPrincipal(long userId, long companyId, String role) {}