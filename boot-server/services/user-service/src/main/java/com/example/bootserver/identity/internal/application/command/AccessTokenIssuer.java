package com.example.bootserver.identity.internal.application.command;

public interface AccessTokenIssuer {

    AccessToken issueAccessToken(Long userId);
}
