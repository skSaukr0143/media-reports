package com.example.mediareport.controller;

import com.example.mediareport.model.ScanFilter;
import com.example.mediareport.model.ScanResult;
import com.example.mediareport.service.ExcelReportService;
import com.example.mediareport.service.PdfReportService;
import com.example.mediareport.service.ScanService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class ReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ScanService scanService;
    private final ExcelReportService excelService;
    private final PdfReportService pdfService;

    public ReportController(ScanService scanService, ExcelReportService excelService, PdfReportService pdfService) {
        this.scanService = scanService;
        this.excelService = excelService;
        this.pdfService = pdfService;
    }

    /** JSON result used by the web page. */
    @GetMapping("/scan")
    public ScanResult scan(ScanFilter filter) {
        return scanService.scan(filter);
    }

    @GetMapping("/report/excel")
    public ResponseEntity<byte[]> excel(ScanFilter filter) throws Exception {
        ScanResult result = scanService.scan(filter);
        return file(excelService.build(result), XLSX, fileName("xlsx"));
    }

    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> pdf(ScanFilter filter) throws Exception {
        ScanResult result = scanService.scan(filter);
        return file(pdfService.build(result), MediaType.APPLICATION_PDF_VALUE, fileName("pdf"));
    }

    /** Folder browser helper (a web page cannot read local paths on its own). */
    @GetMapping("/browse")
    public Map<String, Object> browse(@RequestParam(required = false) String path) {
        Path p = (path == null || path.isBlank())
                ? Paths.get(System.getProperty("user.home"))
                : Paths.get(path.trim());
        p = p.toAbsolutePath().normalize();
        if (!Files.isDirectory(p)) {
            throw new IllegalArgumentException("Folder not found: " + p);
        }
        List<String> dirs;
        try (Stream<Path> s = Files.list(p)) {
            dirs = s.filter(Files::isDirectory)
                    .map(x -> x.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read folder: " + p);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("path", p.toString());
        res.put("parent", p.getParent() == null ? null : p.getParent().toString());
        res.put("directories", dirs);
        return res;
    }

    private ResponseEntity<byte[]> file(byte[] bytes, String contentType, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private String fileName(String ext) {
        return "media-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "." + ext;
    }
}
