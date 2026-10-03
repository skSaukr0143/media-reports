package com.example.mediareport.model;

/** Result of probing a single file. Any value can be null if it could not be determined. */
public record MediaMeta(Double durationSeconds, Integer width, Integer height) {
}
