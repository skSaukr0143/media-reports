package com.example.mediareport.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bound from query-string parameters. */
public class ScanFilter {

    private String path;
    private boolean recursive = true;
    private List<FileKind> types;
    private String extensions;
    private String name;
    private Double minSizeMb;
    private Double maxSizeMb;
    private Double minDurationMin;
    private Double maxDurationMin;
    private String sortBy = "name";
    private String sortDir = "asc";

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public boolean isRecursive() { return recursive; }
    public void setRecursive(boolean recursive) { this.recursive = recursive; }

    public List<FileKind> getTypes() { return types; }
    public void setTypes(List<FileKind> types) { this.types = types; }

    public String getExtensions() { return extensions; }
    public void setExtensions(String extensions) { this.extensions = extensions; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getMinSizeMb() { return minSizeMb; }
    public void setMinSizeMb(Double minSizeMb) { this.minSizeMb = minSizeMb; }

    public Double getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(Double maxSizeMb) { this.maxSizeMb = maxSizeMb; }

    public Double getMinDurationMin() { return minDurationMin; }
    public void setMinDurationMin(Double minDurationMin) { this.minDurationMin = minDurationMin; }

    public Double getMaxDurationMin() { return maxDurationMin; }
    public void setMaxDurationMin(Double maxDurationMin) { this.maxDurationMin = maxDurationMin; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }

    /** Human readable description of the active filters (printed in the reports). */
    public String filterText() {
        List<String> parts = new ArrayList<>();
        if (types == null || types.isEmpty() || types.size() == FileKind.values().length) {
            parts.add("Types: All");
        } else {
            List<String> labels = new ArrayList<>();
            for (FileKind k : types) labels.add(k.label());
            parts.add("Types: " + String.join(", ", labels));
        }
        if (extensions != null && !extensions.isBlank()) {
            parts.add("Extensions: " + extensions.trim().toLowerCase(Locale.ROOT));
        }
        if (name != null && !name.isBlank()) parts.add("Name contains: " + name.trim());
        if (minSizeMb != null) parts.add("Min size: " + minSizeMb + " MB");
        if (maxSizeMb != null) parts.add("Max size: " + maxSizeMb + " MB");
        if (minDurationMin != null) parts.add("Min duration: " + minDurationMin + " min");
        if (maxDurationMin != null) parts.add("Max duration: " + maxDurationMin + " min");
        parts.add("Subfolders: " + (recursive ? "included" : "not included"));
        return String.join("  |  ", parts);
    }
}
