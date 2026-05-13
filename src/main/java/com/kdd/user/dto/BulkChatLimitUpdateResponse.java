package com.kdd.user.dto;

public record BulkChatLimitUpdateResponse(
        int updatedCount,
        int dailyChatLimit
) {
}
