package com.example.bootserver.identity.internal.application.command;

public record CreateUserCommand(String name, String email, Integer age) {
}
