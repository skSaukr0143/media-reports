package com.example.mediareport.model;

public record TypeSummary(FileKind type, long count, long sizeBytes, double durationSeconds) {
}
