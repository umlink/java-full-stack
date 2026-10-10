package com.example.bootserver.identity.internal.application.command;

public record UpdateUserCommand(String name, String email, Integer age) {

    public boolean hasUpdateField() {
        return name != null || email != null || age != null;
    }
}
