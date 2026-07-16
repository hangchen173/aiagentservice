package com.intern.chat;

public record StoredImage(String key, String contentType, String originalName, byte[] data) {
}
