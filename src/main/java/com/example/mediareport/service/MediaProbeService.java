package com.example.mediareport.service;

import com.example.mediareport.model.FileKind;
import com.example.mediareport.model.MediaMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds duration / resolution of a file.
 * Order: ffprobe (if installed) -> pure Java fallbacks (ImageIO, MP4 atom parser, WAV, jaudiotagger).
 */
@Service
public class MediaProbeService {

    private static final Set<String> MP4_FAMILY = Set.of("mp4", "m4v", "mov", "m4a", "3gp");

    private final ObjectMapper mapper = new ObjectMapper();
    private final String ffprobePath;
    private volatile Boolean ffprobeAvailable;

    public MediaProbeService(@Value("${media.ffprobe.path:ffprobe}") String ffprobePath) {
        this.ffprobePath = ffprobePath;
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public MediaMeta probe(Path path, FileKind kind, String ext) {
        try {
            if (kind == FileKind.IMAGE) {
                int[] d = imageSize(path);
                if (d != null) return new MediaMeta(null, d[0], d[1]);
                MediaMeta m = ffprobe(path, kind);
                return m != null ? new MediaMeta(null, m.width(), m.height()) : new MediaMeta(null, null, null);
            }

            MediaMeta viaFfprobe = ffprobe(path, kind);
            if (viaFfprobe != null && viaFfprobe.durationSeconds() != null) {
                return viaFfprobe;
            }

            Double dur = fallbackDuration(path.toFile(), ext);
            Integer w = viaFfprobe != null ? viaFfprobe.width() : null;
            Integer h = viaFfprobe != null ? viaFfprobe.height() : null;
            return new MediaMeta(dur, w, h);
        } catch (Throwable t) {
            return new MediaMeta(null, null, null);
        }
    }

    // ---------------------------------------------------------------- images
    private int[] imageSize(Path path) {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // --------------------------------------------------------------- ffprobe
    private boolean isFfprobeAvailable() {
        Boolean a = ffprobeAvailable;
        if (a == null) {
            try {
                Process p = new ProcessBuilder(ffprobePath, "-version")
                        .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                a = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                a = false;
            }
            ffprobeAvailable = a;
        }
        return a;
    }

    private MediaMeta ffprobe(Path path, FileKind kind) {
        if (!isFfprobeAvailable()) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath, "-v", "error",
                    "-show_entries", "format=duration:stream=codec_type,width,height",
                    "-of", "json",
                    path.toAbsolutePath().toString());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            JsonNode root = mapper.readTree(out);

            Double duration = null;
            JsonNode d = root.path("format").path("duration");
            if (!d.isMissingNode()) {
                try {
                    duration = Double.parseDouble(d.asText());
                } catch (NumberFormatException ignored) {
                    // "N/A"
                }
            }

            Integer w = null, h = null;
            if (kind != FileKind.AUDIO) {
                for (JsonNode s : root.path("streams")) {
                    if (s.path("width").asInt(0) > 0) {
                        w = s.path("width").asInt();
                        h = s.path("height").asInt();
                        break;
                    }
                }
            }
            return new MediaMeta(kind == FileKind.IMAGE ? null : duration, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------ pure-Java fallbacks
    private Double fallbackDuration(File file, String ext) {
        Double d;
        if (MP4_FAMILY.contains(ext)) {
            d = mp4Duration(file);
            if (d != null) return d;
        }
        if ("wav".equals(ext) || "aiff".equals(ext) || "aif".equals(ext)) {
            d = javaSoundDuration(file);
            if (d != null) return d;
        }
        return jaudiotaggerDuration(file);
    }

    private Double javaSoundDuration(File file) {
        try {
            AudioFileFormat f = AudioSystem.getAudioFileFormat(file);
            long frames = f.getFrameLength();
            float rate = f.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) return frames / (double) rate;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Double jaudiotaggerDuration(File file) {
        try {
            org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(file);
            double len = af.getAudioHeader().getPreciseTrackLength();
            return len > 0 ? len : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Reads duration from the 'mvhd' atom inside 'moov' (MP4 / MOV / M4A / 3GP). */
    private Double mp4Duration(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return findMvhd(raf, 0, raf.length());
        } catch (Throwable t) {
            return null;
        }
    }

    private Double findMvhd(RandomAccessFile raf, long start, long end) throws IOException {
        long pos = start;
        while (pos + 8 <= end) {
            raf.seek(pos);
            long size = raf.readInt() & 0xFFFFFFFFL;
            byte[] t = new byte[4];
            raf.readFully(t);
            String type = new String(t, StandardCharsets.ISO_8859_1);
            long header = 8;
            if (size == 1) {
                size = raf.readLong();
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header) return null;

            if (type.equals("moov")) {
                return findMvhd(raf, pos + header, Math.min(pos + size, end));
            }
            if (type.equals("mvhd")) {
                int version = raf.readUnsignedByte();
                raf.skipBytes(3);
                long timescale;
                long duration;
                if (version == 1) {
                    raf.skipBytes(16);
                    timescale = raf.readInt() & 0xFFFFFFFFL;
                    duration = raf.readLong();
                } else {
                    raf.skipBytes(8);
                    timescale = raf.readInt() & 0xFFFFFFFFL;
                    duration = raf.readInt() & 0xFFFFFFFFL;
                }
                return timescale > 0 ? duration / (double) timescale : null;
            }
            pos += size;
        }
        return null;
    }
}
