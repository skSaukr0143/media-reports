package com.example.mediareport.model;

import java.util.Optional;
import java.util.Set;

public enum FileKind {
    VIDEO("Video", Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp", "mts", "m2ts", "vob")),
    IMAGE("Image", Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "svg", "heic", "heif", "ico")),
    AUDIO("Audio", Set.of("mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "wma", "opus", "aiff", "aif", "amr"));

    private final String label;
    private final Set<String> extensions;

    FileKind(String label, Set<String> extensions) {
        this.label = label;
        this.extensions = extensions;
    }

    public String label() {
        return label;
    }

    public static Optional<FileKind> fromExtension(String ext) {
        for (FileKind k : values()) {
            if (k.extensions.contains(ext)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
