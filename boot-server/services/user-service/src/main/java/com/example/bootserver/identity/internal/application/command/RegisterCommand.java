package com.example.bootserver.identity.internal.application.command;

public record RegisterCommand(String username, String email, String password) {
}
