package com.example.bootserver.identity.internal.application.query;

import java.util.List;

public record UserPage(long page, long size, long total, List<UserView> records) {
}
