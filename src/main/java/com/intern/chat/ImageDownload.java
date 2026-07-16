package com.intern.chat;

public record ImageDownload(byte[] data, String contentType, String filename) {
}
