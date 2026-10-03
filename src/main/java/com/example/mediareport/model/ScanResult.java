package com.example.mediareport.model;

import java.util.List;

public record ScanResult(
        String folder,
        String generatedAt,
        String filterText,
        List<MediaFileInfo> files,
        Summary summary) {
}
