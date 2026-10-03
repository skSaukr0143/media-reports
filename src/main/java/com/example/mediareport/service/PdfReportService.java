package com.example.mediareport.service;

import com.example.mediareport.model.*;
import com.example.mediareport.util.Fmt;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class PdfReportService {

    private static final Color BLUE = new Color(31, 56, 100);
    private static final Color GREY = new Color(235, 238, 243);
    private static final Color YELLOW = new Color(255, 248, 214);

    private static final String[] HEADERS = {
            "#", "Name", "Type", "Ext", "Size", "Duration", "Resolution", "Modified", "Folder"};
    private static final float[] WIDTHS = {4, 27, 7, 6, 9, 9, 10, 12, 24};

    public byte[] build(ScanResult r) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 32, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, out);

        final Font pageFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_CENTER,
                        new Phrase("Page " + w.getPageNumber(), pageFont),
                        (d.right() + d.left()) / 2, d.bottom() - 20, 0);
            }
        });

        doc.open();

        Font titleF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE);
        Font metaF = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font headF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellF = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.BLACK);
        Font boldF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font sectionF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE);

        doc.add(new Paragraph("Media Folder Report", titleF));
        doc.add(new Paragraph("Folder: " + r.folder(), metaF));
        doc.add(new Paragraph("Generated: " + r.generatedAt(), metaF));
        Paragraph filters = new Paragraph("Filters: " + r.filterText(), metaF);
        filters.setSpacingAfter(10);
        doc.add(filters);

        // ---- data table
        PdfPTable table = new PdfPTable(WIDTHS);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : HEADERS) {
            PdfPCell c = new PdfPCell(new Phrase(h, headF));
            c.setBackgroundColor(BLUE);
            c.setPadding(4);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
        int n = 1;
        for (MediaFileInfo m : r.files()) {
            Color bg = (n % 2 == 0) ? GREY : Color.WHITE;
            String[] vals = {
                    String.valueOf(n++), m.name(), m.type().label(), m.extension(),
                    Fmt.size(m.sizeBytes()), Fmt.duration(m.durationSeconds()),
                    Fmt.resolution(m.width(), m.height()), Fmt.dateTime(m.lastModified()), m.folder()};
            for (String v : vals) {
                PdfPCell c = new PdfPCell(new Phrase(v, cellF));
                c.setBackgroundColor(bg);
                c.setPadding(3);
                table.addCell(c);
            }
        }
        if (r.files().isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No files matched the selected filters.", cellF));
            c.setColspan(HEADERS.length);
            c.setPadding(8);
            table.addCell(c);
        }
        doc.add(table);

        // ---- summary (bottom)
        Summary s = r.summary();
        Paragraph sp = new Paragraph("Summary", sectionF);
        sp.setSpacingBefore(16);
        sp.setSpacingAfter(6);
        sp.setKeepTogether(true);
        doc.add(sp);

        PdfPTable totals = new PdfPTable(new float[]{30, 20});
        totals.setWidthPercentage(40);
        totals.setHorizontalAlignment(Element.ALIGN_LEFT);
        totals.setKeepTogether(true);
        addRow(totals, "Total files", String.valueOf(s.totalFiles()), boldF);
        addRow(totals, "Total size", Fmt.size(s.totalSizeBytes()), boldF);
        addRow(totals, "Total length (video + audio)", Fmt.duration(s.totalDurationSeconds()), boldF);
        if (s.unknownDurationCount() > 0) {
            addRow(totals, "Files with unknown length", String.valueOf(s.unknownDurationCount()), boldF);
        }
        doc.add(totals);

        Paragraph gap = new Paragraph(" ");
        gap.setSpacingAfter(4);
        doc.add(gap);

        PdfPTable byType = new PdfPTable(new float[]{18, 12, 20, 20});
        byType.setWidthPercentage(55);
        byType.setHorizontalAlignment(Element.ALIGN_LEFT);
        byType.setKeepTogether(true);
        for (String h : new String[]{"Type", "Count", "Total size", "Total length"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, headF));
            c.setBackgroundColor(BLUE);
            c.setPadding(4);
            byType.addCell(c);
        }
        for (TypeSummary t : s.byType()) {
            addCell(byType, t.type().label() + "s", boldF, GREY);
            addCell(byType, String.valueOf(t.count()), boldF, YELLOW);
            addCell(byType, Fmt.size(t.sizeBytes()), boldF, YELLOW);
            addCell(byType, t.type() == FileKind.IMAGE ? "-" : Fmt.duration(t.durationSeconds()), boldF, YELLOW);
        }
        addCell(byType, "TOTAL", boldF, GREY);
        addCell(byType, String.valueOf(s.totalFiles()), boldF, YELLOW);
        addCell(byType, Fmt.size(s.totalSizeBytes()), boldF, YELLOW);
        addCell(byType, Fmt.duration(s.totalDurationSeconds()), boldF, YELLOW);
        doc.add(byType);

        doc.close();
        return out.toByteArray();
    }

    private void addRow(PdfPTable t, String label, String value, Font f) {
        addCell(t, label, f, GREY);
        addCell(t, value, f, YELLOW);
    }

    private void addCell(PdfPTable t, String text, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        t.addCell(c);
    }
}
