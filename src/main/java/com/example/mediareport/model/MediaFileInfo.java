package com.example.mediareport.model;

public record MediaFileInfo(
        String name,
        String folder,
        String fullPath,
        FileKind type,
        String extension,
        long sizeBytes,
        Double durationSeconds,
        Integer width,
        Integer height,
        long lastModified) {
}
