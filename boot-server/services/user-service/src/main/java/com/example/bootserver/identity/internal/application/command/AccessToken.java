package com.example.bootserver.identity.internal.application.command;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {
}
