package com.example.mediareport.service;

import com.example.mediareport.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScanService {

    private record Candidate(Path path, FileKind kind, String ext, long size, long modified) {
    }

    private final MediaProbeService probeService;

    public ScanService(MediaProbeService probeService) {
        this.probeService = probeService;
    }

    public ScanResult scan(ScanFilter f) {
        final Path root = resolveRoot(f.getPath());

        final Set<FileKind> kinds = (f.getTypes() == null || f.getTypes().isEmpty())
                ? EnumSet.allOf(FileKind.class)
                : EnumSet.copyOf(f.getTypes());
        final Set<String> exts = parseExtensions(f.getExtensions());
        final long minBytes = f.getMinSizeMb() == null ? 0 : (long) (f.getMinSizeMb() * 1024 * 1024);
        final long maxBytes = f.getMaxSizeMb() == null ? Long.MAX_VALUE : (long) (f.getMaxSizeMb() * 1024 * 1024);
        final String nameQuery = f.getName() == null ? "" : f.getName().trim().toLowerCase(Locale.ROOT);
        final Double minDur = f.getMinDurationMin() == null ? null : f.getMinDurationMin() * 60;
        final Double maxDur = f.getMaxDurationMin() == null ? null : f.getMaxDurationMin() * 60;

        // 1) cheap pass: walk the tree and filter by name / extension / size
        final List<Candidate> candidates = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class),
                    f.isRecursive() ? Integer.MAX_VALUE : 1,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                            String fn = file.getFileName().toString();
                            int dot = fn.lastIndexOf('.');
                            if (dot < 0) return FileVisitResult.CONTINUE;
                            String ext = fn.substring(dot + 1).toLowerCase(Locale.ROOT);

                            Optional<FileKind> kind = FileKind.fromExtension(ext);
                            if (kind.isEmpty() || !kinds.contains(kind.get())) return FileVisitResult.CONTINUE;
                            if (!exts.isEmpty() && !exts.contains(ext)) return FileVisitResult.CONTINUE;
                            if (attrs.size() < minBytes || attrs.size() > maxBytes) return FileVisitResult.CONTINUE;
                            if (!nameQuery.isEmpty() && !fn.toLowerCase(Locale.ROOT).contains(nameQuery))
                                return FileVisitResult.CONTINUE;

                            candidates.add(new Candidate(file, kind.get(), ext, attrs.size(),
                                    attrs.lastModifiedTime().toMillis()));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE; // skip unreadable files / folders
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Could not read folder: " + e.getMessage(), e);
        }

        // 2) expensive pass: read duration + resolution
        List<MediaFileInfo> files = candidates.parallelStream()
                .map(c -> toInfo(root, c))
                .filter(m -> durationOk(m, minDur, maxDur))
                .sorted(comparator(f.getSortBy(), f.getSortDir()))
                .collect(Collectors.toList());

        return new ScanResult(
                root.toString(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                f.filterText(),
                files,
                summarize(files));
    }

    // ------------------------------------------------------------------ helpers
    private Path resolveRoot(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Enter a folder path.");
        }
        Path root;
        try {
            root = Paths.get(path.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("This is not a valid path: " + path);
        }
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Folder not found: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("This path is a file, not a folder: " + root);
        }
        return root;
    }

    private Set<String> parseExtensions(String csv) {
        Set<String> out = new HashSet<>();
        if (csv == null) return out;
        for (String s : csv.split("[,;\\s]+")) {
            String e = s.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith("*.")) e = e.substring(2);
            if (e.startsWith(".")) e = e.substring(1);
            if (!e.isEmpty()) out.add(e);
        }
        return out;
    }

    private MediaFileInfo toInfo(Path root, Candidate c) {
        MediaMeta meta = probeService.probe(c.path(), c.kind(), c.ext());
        Path parent = c.path().getParent();
        String rel = parent == null ? "." : root.relativize(parent).toString();
        if (rel.isEmpty()) rel = ".";
        return new MediaFileInfo(
                c.path().getFileName().toString(),
                rel,
                c.path().toString(),
                c.kind(),
                c.ext(),
                c.size(),
                meta.durationSeconds(),
                meta.width(),
                meta.height(),
                c.modified());
    }

    private boolean durationOk(MediaFileInfo m, Double min, Double max) {
        if (min == null && max == null) return true;
        if (m.durationSeconds() == null) return false;
        if (min != null && m.durationSeconds() < min) return false;
        return max == null || m.durationSeconds() <= max;
    }

    private Comparator<MediaFileInfo> comparator(String sortBy, String dir) {
        String key = sortBy == null ? "name" : sortBy;
        Comparator<MediaFileInfo> c;
        switch (key) {
            case "size":
                c = Comparator.comparingLong(MediaFileInfo::sizeBytes);
                break;
            case "duration":
                c = Comparator.comparing(MediaFileInfo::durationSeconds,
                        Comparator.nullsFirst(Comparator.<Double>naturalOrder()));
                break;
            case "type":
                c = Comparator.comparing((MediaFileInfo m) -> m.type().name());
                break;
            case "extension":
                c = Comparator.comparing(MediaFileInfo::extension);
                break;
            case "modified":
                c = Comparator.comparingLong(MediaFileInfo::lastModified);
                break;
            default:
                c = Comparator.comparing((MediaFileInfo m) -> m.name().toLowerCase(Locale.ROOT));
        }
        return "desc".equalsIgnoreCase(dir) ? c.reversed() : c;
    }

    private Summary summarize(List<MediaFileInfo> files) {
        long totalSize = 0;
        double totalDur = 0;
        long unknown = 0;
        List<TypeSummary> byType = new ArrayList<>();

        for (FileKind kind : FileKind.values()) {
            long count = 0, size = 0;
            double dur = 0;
            for (MediaFileInfo m : files) {
                if (m.type() != kind) continue;
                count++;
                size += m.sizeBytes();
                if (m.durationSeconds() != null) {
                    dur += m.durationSeconds();
                } else if (kind != FileKind.IMAGE) {
                    unknown++;
                }
            }
            byType.add(new TypeSummary(kind, count, size, dur));
            totalSize += size;
            totalDur += dur;
        }
        return new Summary(files.size(), totalSize, totalDur, unknown, byType);
    }
}
