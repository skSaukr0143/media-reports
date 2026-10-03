# Media Folder Report (Spring Boot)

Enter a folder path, scan it for **videos, images and audios**, and download an **Excel** or **PDF** report.
Every report ends with a summary: file counts, total size and total video/audio length.

## Requirements
- Java 17+
- Maven 3.8+
- (Optional, recommended) FFmpeg's `ffprobe` on your PATH. It reads length and resolution for every format.
  Without it the app still works: MP4/MOV/M4A/3GP, WAV and MP3/FLAC/OGG are read in pure Java; other
  video formats (mkv, avi, wmv ...) will show "-" for length.

## Run
```
mvn spring-boot:run
```
Open http://localhost:8080

If ffprobe is not on your PATH, set its full path in `src/main/resources/application.properties`:
```
media.ffprobe.path=C:/ffmpeg/bin/ffprobe.exe
```

## Features
- Folder path input plus a folder browser
- Filters: file type, extensions, name contains, size range (MB), length range (minutes), include subfolders
- Table with sorting, paging, a totals row and a summary table
- Click a summary card (Videos, Images, Audios) to scan only that type
- Excel and PDF downloads use the same filters as the screen

## API
| Endpoint | Purpose |
|---|---|
| `GET /api/scan` | JSON result for the web page |
| `GET /api/report/excel` | `.xlsx` download |
| `GET /api/report/pdf` | `.pdf` download |
| `GET /api/browse?path=` | lists sub-folders for the folder picker |

Query parameters: `path, recursive, types (VIDEO,IMAGE,AUDIO), extensions, name, minSizeMb, maxSizeMb, minDurationMin, maxDurationMin, sortBy, sortDir`

## Security note
This app reads any folder the Java process can access. Run it only on your own machine or a trusted network.
PDF uses the built-in Helvetica font, so file names with non-Latin characters may not render in the PDF (Excel shows them correctly).
