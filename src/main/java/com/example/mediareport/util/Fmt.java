package com.example.mediareport.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Fmt {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Fmt() {
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double v = bytes;
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.2f %s", v, units[i]);
    }

    /** HH:MM:SS (hours may exceed 24). Returns "-" for null. */
    public static String duration(Double seconds) {
        if (seconds == null) return "-";
        long total = Math.round(seconds);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    public static String resolution(Integer w, Integer h) {
        return (w == null || h == null) ? "-" : w + " x " + h;
    }

    public static String dateTime(long epochMs) {
        return DT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }
}
