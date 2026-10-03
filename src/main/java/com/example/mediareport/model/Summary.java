package com.example.mediareport.model;

import java.util.List;

public record Summary(
        long totalFiles,
        long totalSizeBytes,
        double totalDurationSeconds,
        long unknownDurationCount,
        List<TypeSummary> byType) {
}
