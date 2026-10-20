package jp.local.fax2840usb;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

final class TableFileToPdf {
    static final float MIN_FONT_SIZE_PT = 9f;

    private static final int PAGE_WIDTH_PT = 842;
    private static final int PAGE_HEIGHT_PT = 595;
    private static final float MARGIN_PT = 24f;
    private static final float TITLE_HEIGHT_PT = 24f;
    private static final float ROW_HEIGHT_PT = 18f;
    private static final float HEADER_HEIGHT_PT = 20f;
    private static final float CELL_PADDING_PT = 4f;
    private static final float MIN_COL_WIDTH_PT = 54f;
    private static final float MAX_COL_WIDTH_PT = 180f;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_COLS = 128;

    static final class Result {
        final File file;
        final int pageCount;
        final String sourceLabel;

        Result(File file, int pageCount, String sourceLabel) {
            this.file = file;
            this.pageCount = pageCount;
            this.sourceLabel = sourceLabel;
        }
    }

    private TableFileToPdf() {}

    static Result convert(Context context, Uri uri, File outputPdf) throws IOException {
        if (context == null || uri == null || outputPdf == null) {
            throw new IOException("表ファイルを開けません");
        }

        String name = displayName(context, uri);
        String mime = context.getContentResolver().getType(uri);
        String lowerName = name == null ? "" : name.toLowerCase(Locale.US);
        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.US);

        List<List<String>> rows;
        String sourceLabel;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("表ファイルを読み込めません");
            if (lowerName.endsWith(".xlsx")
                    || lowerMime.contains("spreadsheetml")
                    || lowerMime.contains("excel")) {
                rows = readXlsx(in);
                sourceLabel = "Excel";
            } else {
                rows = readCsv(in);
                sourceLabel = "CSV";
            }
        }

        normalize(rows);
        if (rows.isEmpty()) throw new IOException("印刷できるデータがありません");
        int pages = render(rows, outputPdf, sourceLabel);
        return new Result(outputPdf, pages, sourceLabel);
    }

    static List<List<String>> readCsv(InputStream in) throws IOException {
        byte[] bytes = readAll(in, 16 * 1024 * 1024);
        String text = decodeText(bytes);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') text = text.substring(1);

        char delimiter = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                if (rows.size() >= MAX_ROWS) break;
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (rows.size() < MAX_ROWS && (field.length() > 0 || !row.isEmpty())) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    static List<List<String>> readXlsx(InputStream in) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> sheets = new ArrayList<>();

        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equals(name)
                        || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))) {
                    byte[] data = readAll(zip, 24 * 1024 * 1024);
                    entries.put(name, data);
                    if (name.startsWith("xl/worksheets/sheet")) sheets.add(name);
                }
                zip.closeEntry();
            }
        }

        if (sheets.isEmpty()) throw new IOException("Excelワークシートが見つかりません");
        Collections.sort(sheets, Comparator.naturalOrder());
        List<String> shared = entries.containsKey("xl/sharedStrings.xml")
                ? parseSharedStrings(entries.get("xl/sharedStrings.xml"))
                : new ArrayList<>();
        return parseWorksheet(entries.get(sheets.get(0)), shared);
    }

    private static List<String> parseSharedStrings(byte[] xml) throws IOException {
        Document doc = parseXml(xml);
        NodeList items = doc.getElementsByTagName("si");
        List<String> values = new ArrayList<>(items.getLength());
        for (int i = 0; i < items.getLength(); i++) {
            values.add(allText((Element) items.item(i), "t"));
        }
        return values;
    }

    private static List<List<String>> parseWorksheet(byte[] xml, List<String> shared) throws IOException {
        if (xml == null) throw new IOException("Excelワークシートを読み込めません");
        Document doc = parseXml(xml);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<>();

        for (int r = 0; r < rowNodes.getLength() && rows.size() < MAX_ROWS; r++) {
            Element rowEl = (Element) rowNodes.item(r);
            NodeList children = rowEl.getChildNodes();
            List<String> row = new ArrayList<>();

            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element) || !"c".equals(node.getNodeName())) continue;
                Element cell = (Element) node;
                int col = columnIndex(cell.getAttribute("r"));
                if (col < 0 || col >= MAX_COLS) continue;
                while (row.size() <= col) row.add("");

                String type = cell.getAttribute("t");
                String value;
                if ("inlineStr".equals(type)) {
                    value = allText(cell, "t");
                } else {
                    String raw = firstText(cell, "v");
                    if ("s".equals(type)) {
                        int index = parseInt(raw, -1);
                        value = index >= 0 && index < shared.size() ? shared.get(index) : raw;
                    } else if ("b".equals(type)) {
                        value = "1".equals(raw) ? "TRUE" : "FALSE";
                    } else {
                        value = raw;
                    }
                }
                row.set(col, value == null ? "" : value);
            }
            rows.add(row);
        }
        return rows;
    }

    private static int render(List<List<String>> rows, File outputPdf, String sourceLabel) throws IOException {
        int columns = 0;
        for (List<String> row : rows) columns = Math.max(columns, row.size());
        if (columns <= 0) throw new IOException("印刷できる列がありません");

        float[] widths = columnWidths(rows, columns);
        List<int[]> groups = columnGroups(widths);
        int firstDataRow = rows.size() > 1 ? 1 : rows.size();
        float usableHeight = PAGE_HEIGHT_PT - MARGIN_PT * 2 - TITLE_HEIGHT_PT - HEADER_HEIGHT_PT;
        int rowsPerPage = Math.max(1, (int)Math.floor(usableHeight / ROW_HEIGHT_PT));
        int dataRows = Math.max(0, rows.size() - firstDataRow);
        int verticalPages = Math.max(1, (int)Math.ceil(dataRows / (double) rowsPerPage));

        PdfDocument pdf = new PdfDocument();
        Paint grid = new Paint();
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(0.6f);
        grid.setColor(Color.rgb(130, 130, 130));

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(MIN_FONT_SIZE_PT);
        text.setColor(Color.BLACK);

        Paint headerFill = new Paint();
        headerFill.setStyle(Paint.Style.FILL);
        headerFill.setColor(Color.rgb(230, 230, 230));

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setTextSize(11f);
        title.setColor(Color.BLACK);
        title.setFakeBoldText(true);

        int pageNo = 0;
        try {
            for (int[] group : groups) {
                for (int vp = 0; vp < verticalPages; vp++) {
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                            PAGE_WIDTH_PT, PAGE_HEIGHT_PT, ++pageNo).create();
                    PdfDocument.Page page = pdf.startPage(info);
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);

                    String pageTitle = sourceLabel + "  列 " + (group[0] + 1) + "〜" + group[1]
                            + "  (" + (vp + 1) + "/" + verticalPages + ")";
                    canvas.drawText(pageTitle, MARGIN_PT, MARGIN_PT + 12f, title);

                    float y = MARGIN_PT + TITLE_HEIGHT_PT;
                    drawRow(canvas, rows.get(0), group[0], group[1], widths,
                            y, HEADER_HEIGHT_PT, text, grid, headerFill, true);
                    y += HEADER_HEIGHT_PT;

                    int start = firstDataRow + vp * rowsPerPage;
                    int end = Math.min(rows.size(), start + rowsPerPage);
                    for (int r = start; r < end; r++) {
                        drawRow(canvas, rows.get(r), group[0], group[1], widths,
                                y, ROW_HEIGHT_PT, text, grid, null, false);
                        y += ROW_HEIGHT_PT;
                    }
                    pdf.finishPage(page);
                }
            }

            try (FileOutputStream out = new FileOutputStream(outputPdf, false)) {
                pdf.writeTo(out);
                out.flush();
            }
        } finally {
            pdf.close();
        }
        return pageNo;
    }

    private static void drawRow(Canvas canvas, List<String> row,
                                int startCol, int endCol, float[] widths,
                                float y, float height, Paint text, Paint grid,
                                Paint fill, boolean bold) {
        float x = MARGIN_PT;
        text.setFakeBoldText(bold);
        for (int c = startCol; c < endCol; c++) {
            float w = widths[c];
            if (fill != null) canvas.drawRect(x, y, x + w, y + height, fill);
            canvas.drawRect(x, y, x + w, y + height, grid);

            String value = c < row.size() ? row.get(c) : "";
            String fitted = fitText(value, text, Math.max(1f, w - CELL_PADDING_PT * 2));
            int save = canvas.save();
            canvas.clipRect(x + CELL_PADDING_PT, y + 1f,
                    x + w - CELL_PADDING_PT, y + height - 1f);
            canvas.drawText(fitted, x + CELL_PADDING_PT, y + height - 5f, text);
            canvas.restoreToCount(save);
            x += w;
        }
        text.setFakeBoldText(false);
    }

    private static float[] columnWidths(List<List<String>> rows, int columns) {
        float[] widths = new float[columns];
        for (int c = 0; c < columns; c++) widths[c] = MIN_COL_WIDTH_PT;

        int sampleRows = Math.min(rows.size(), 250);
        for (int r = 0; r < sampleRows; r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < Math.min(columns, row.size()); c++) {
                String value = row.get(c);
                int chars = value == null ? 0 : Math.min(28, value.length());
                float estimated = 16f + chars * 5.5f;
                widths[c] = Math.max(widths[c], Math.min(MAX_COL_WIDTH_PT, estimated));
            }
        }
        return widths;
    }

    static List<int[]> columnGroups(float[] widths) {
        List<int[]> groups = new ArrayList<>();
        float available = PAGE_WIDTH_PT - MARGIN_PT * 2;
        int start = 0;
        while (start < widths.length) {
            float used = 0f;
            int end = start;
            while (end < widths.length) {
                float next = Math.min(MAX_COL_WIDTH_PT, Math.max(MIN_COL_WIDTH_PT, widths[end]));
                if (end > start && used + next > available) break;
                used += next;
                end++;
            }
            if (end == start) end++;
            groups.add(new int[]{start, end});
            start = end;
        }
        return groups;
    }

    private static String fitText(String value, Paint paint, float maxWidth) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        if (paint.measureText(clean) <= maxWidth) return clean;
        String ellipsis = "…";
        int end = clean.length();
        while (end > 0 && paint.measureText(clean.substring(0, end) + ellipsis) > maxWidth) end--;
        return end <= 0 ? ellipsis : clean.substring(0, end) + ellipsis;
    }

    private static void normalize(List<List<String>> rows) {
        while (!rows.isEmpty() && isBlank(rows.get(rows.size() - 1))) rows.remove(rows.size() - 1);
        for (List<String> row : rows) {
            while (row.size() > MAX_COLS) row.remove(row.size() - 1);
        }
    }

    private static boolean isBlank(List<String> row) {
        for (String v : row) if (v != null && !v.trim().isEmpty()) return false;
        return true;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {}
        return uri.getLastPathSegment();
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String line = end >= 0 ? text.substring(0, end) : text;
        int commas = count(line, ',');
        int tabs = count(line, '\t');
        int semis = count(line, ';');
        if (tabs > commas && tabs >= semis) return '\t';
        if (semis > commas) return ';';
        return ',';
    }

    private static int count(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ch) n++;
        return n;
    }

    private static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS932"));
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32 * 1024];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) throw new IOException("ファイルが大きすぎます");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Document parseXml(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (Exception ignored) {}
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (Exception ignored) {}
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (Exception ignored) {}
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IOException("Excel XMLを解析できません: " + e.getMessage(), e);
        }
    }

    private static String firstText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private static String allText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) out.append(nodes.item(i).getTextContent());
        return out.toString();
    }

    private static int columnIndex(String ref) {
        if (ref == null || ref.isEmpty()) return -1;
        int value = 0;
        int letters = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                value = value * 26 + (ch - 'A' + 1);
                letters++;
            } else if (ch >= 'a' && ch <= 'z') {
                value = value * 26 + (ch - 'a' + 1);
                letters++;
            } else break;
        }
        return letters == 0 ? -1 : value - 1;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException e) { return fallback; }
    }
}
