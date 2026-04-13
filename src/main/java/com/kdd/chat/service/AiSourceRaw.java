package com.kdd.chat.service;

public record AiSourceRaw(
        Long docId,
        Long chunkId,
        String docName,
        Integer page
) {
}
