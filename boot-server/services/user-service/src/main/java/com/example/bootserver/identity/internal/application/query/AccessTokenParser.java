package com.example.bootserver.identity.internal.application.query;

public interface AccessTokenParser {

    Long parseUserId(String token);
}
