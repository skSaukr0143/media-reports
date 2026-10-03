package com.example.mediareport.service;

import com.example.mediareport.model.*;
import com.example.mediareport.util.Fmt;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ExcelReportService {

    private static final String[] HEADERS = {
            "#", "Name", "Type", "Ext", "Size", "Size (bytes)", "Duration", "Duration (sec)",
            "Resolution", "Modified", "Folder"};
    private static final int[] WIDTHS = {6, 42, 10, 8, 14, 16, 12, 16, 14, 18, 50};

    public byte[] build(ScanResult r) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        try {
            Sheet sheet = wb.createSheet("Media Report");

            CellStyle title = style(wb, true, 16, IndexedColors.DARK_BLUE.getIndex(), null, false);
            CellStyle meta = style(wb, false, 10, IndexedColors.GREY_50_PERCENT.getIndex(), null, false);
            CellStyle head = style(wb, true, 11, IndexedColors.WHITE.getIndex(), IndexedColors.DARK_BLUE.getIndex(), true);
            CellStyle text = style(wb, false, 10, IndexedColors.BLACK.getIndex(), null, false);
            CellStyle num = style(wb, false, 10, IndexedColors.BLACK.getIndex(), null, false);
            num.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            CellStyle sumHead = style(wb, true, 11, IndexedColors.WHITE.getIndex(), IndexedColors.GREY_80_PERCENT.getIndex(), true);
            CellStyle sumLabel = style(wb, true, 10, IndexedColors.BLACK.getIndex(), IndexedColors.GREY_25_PERCENT.getIndex(), false);
            CellStyle sumVal = style(wb, true, 10, IndexedColors.BLACK.getIndex(), IndexedColors.LIGHT_YELLOW.getIndex(), false);
            CellStyle sumNum = style(wb, true, 10, IndexedColors.BLACK.getIndex(), IndexedColors.LIGHT_YELLOW.getIndex(), false);
            sumNum.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            for (int i = 0; i < WIDTHS.length; i++) sheet.setColumnWidth(i, WIDTHS[i] * 256);

            int row = 0;
            cell(sheet.createRow(row++), 0, "Media Folder Report", title);
            cell(sheet.createRow(row++), 0, "Folder: " + r.folder() + "    Generated: " + r.generatedAt(), meta);
            cell(sheet.createRow(row++), 0, "Filters: " + r.filterText(), meta);
            row++;

            // ---- header
            int headerRow = row;
            Row h = sheet.createRow(row++);
            for (int i = 0; i < HEADERS.length; i++) cell(h, i, HEADERS[i], head);

            // ---- data
            int n = 1;
            for (MediaFileInfo m : r.files()) {
                Row x = sheet.createRow(row++);
                cell(x, 0, n++, num);
                cell(x, 1, m.name(), text);
                cell(x, 2, m.type().label(), text);
                cell(x, 3, m.extension(), text);
                cell(x, 4, Fmt.size(m.sizeBytes()), text);
                cell(x, 5, m.sizeBytes(), num);
                cell(x, 6, Fmt.duration(m.durationSeconds()), text);
                if (m.durationSeconds() != null) {
                    cell(x, 7, Math.round(m.durationSeconds()), num);
                } else {
                    cell(x, 7, "-", text);
                }
                cell(x, 8, Fmt.resolution(m.width(), m.height()), text);
                cell(x, 9, Fmt.dateTime(m.lastModified()), text);
                cell(x, 10, m.folder(), text);
            }
            int lastData = row - 1;
            if (lastData >= headerRow + 1) {
                sheet.setAutoFilter(new CellRangeAddress(headerRow, lastData, 0, HEADERS.length - 1));
            }
            sheet.createFreezePane(0, headerRow + 1);

            // ---- summary (bottom)
            row += 1;
            Summary s = r.summary();
            Row sh = sheet.createRow(row++);
            cell(sh, 1, "SUMMARY", sumHead);
            cell(sh, 2, "", sumHead);
            sheet.addMergedRegion(new CellRangeAddress(sh.getRowNum(), sh.getRowNum(), 1, 2));

            summaryLine(sheet, row++, "Total files", s.totalFiles(), sumLabel, sumNum);
            summaryLine(sheet, row++, "Total size", Fmt.size(s.totalSizeBytes()), sumLabel, sumVal);
            summaryLine(sheet, row++, "Total length (video + audio)", Fmt.duration(s.totalDurationSeconds()), sumLabel, sumVal);
            if (s.unknownDurationCount() > 0) {
                summaryLine(sheet, row++, "Files with unknown length", s.unknownDurationCount(), sumLabel, sumNum);
            }

            row++;
            Row bh = sheet.createRow(row++);
            cell(bh, 1, "Type", sumHead);
            cell(bh, 2, "Count", sumHead);
            cell(bh, 3, "", sumHead);
            cell(bh, 4, "Total size", sumHead);
            cell(bh, 6, "Total length", sumHead);
            for (TypeSummary t : s.byType()) {
                Row b = sheet.createRow(row++);
                cell(b, 1, t.type().label() + "s", sumLabel);
                cell(b, 2, t.count(), sumNum);
                cell(b, 4, Fmt.size(t.sizeBytes()), sumVal);
                cell(b, 6, t.type() == FileKind.IMAGE ? "-" : Fmt.duration(t.durationSeconds()), sumVal);
            }
            Row tot = sheet.createRow(row);
            cell(tot, 1, "TOTAL", sumLabel);
            cell(tot, 2, s.totalFiles(), sumNum);
            cell(tot, 4, Fmt.size(s.totalSizeBytes()), sumVal);
            cell(tot, 6, Fmt.duration(s.totalDurationSeconds()), sumVal);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    // ---------------------------------------------------------------- helpers
    private void summaryLine(Sheet sheet, int rowIdx, String label, Object value, CellStyle ls, CellStyle vs) {
        Row r = sheet.createRow(rowIdx);
        cell(r, 1, label, ls);
        cell(r, 2, value, vs);
    }

    private CellStyle style(Workbook wb, boolean bold, int size, short fontColor, Short bg, boolean center) {
        Font f = wb.createFont();
        f.setBold(bold);
        f.setFontHeightInPoints((short) size);
        f.setColor(fontColor);
        CellStyle st = wb.createCellStyle();
        st.setFont(f);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        if (center) st.setAlignment(HorizontalAlignment.CENTER);
        if (bg != null) {
            st.setFillForegroundColor(bg);
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return st;
    }

    private void cell(Row row, int col, Object value, CellStyle st) {
        Cell c = row.createCell(col);
        if (value instanceof Number num) {
            c.setCellValue(num.doubleValue());
        } else {
            c.setCellValue(value == null ? "" : value.toString());
        }
        c.setCellStyle(st);
    }
}
