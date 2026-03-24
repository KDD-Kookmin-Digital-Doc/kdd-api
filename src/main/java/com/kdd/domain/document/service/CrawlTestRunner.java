package com.kdd.domain.document.service;

import com.kdd.domain.document.entity.Document;
import com.kdd.domain.document.entity.DocumentChunk;
import com.kdd.domain.document.entity.DocumentSource;
import com.kdd.domain.document.entity.DocumentStatus;
import com.kdd.domain.document.repository.DocumentChunkRepository;
import com.kdd.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Profile("crawl-test")
@RequiredArgsConstructor
public class CrawlTestRunner implements CommandLineRunner {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    private static final String OUTPUT_DIR = "crawl-output";
    private static final float MARGIN = 50, FONT_SIZE = 11, TITLE_FONT_SIZE = 16, LINE_SPACING = 16;
    private static final int CHUNK_SIZE = 550, CHUNK_OVERLAP = 100;

    // 카테고리 정의: 메인카테고리, baseUrl, 서브탭 sc코드들
    private static final List<CategoryDef> CATEGORIES = List.of(
            new CategoryDef("SW학사공지", "https://cs.kookmin.ac.kr/news/notice/",
                    Map.of("326", "학부", "327", "SW중심대학")),
            new CategoryDef("SW취업공지", "https://cs.kookmin.ac.kr/news/jobs/",
                    Map.of("328", "취업", "329", "취업후기", "330", "SW중심대학")),
            new CategoryDef("SW장학공지", "https://cs.kookmin.ac.kr/news/scholarship/",
                    Map.of("331", "일반", "332", "SW중심대학")),
            new CategoryDef("SW특강및행사", "https://cs.kookmin.ac.kr/news/event/",
                    Map.of("333", "외부 행사 및 활동", "334", "내부 행사 및 활동")),
            new CategoryDef("자료실", "https://cs.kookmin.ac.kr/news/form/",
                    Map.of("249", "양식함", "250", "참고자료"))
    );

    record CategoryDef(String name, String baseUrl, Map<String, String> subTabs) {}

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 전체 크롤링 시작 ===\n");
        Files.createDirectories(Path.of(OUTPUT_DIR));

        int totalSuccess = 0, totalSkip = 0;

        for (CategoryDef cat : CATEGORIES) {
            System.out.println("\n──── " + cat.name() + " ────");

            // 1. 전체 탭에서 공지 ID 수집
            Set<String> allIds = collectPostIds(cat.baseUrl());
            System.out.println("전체: " + allIds.size() + "개");

            // 2. 각 서브탭별 공지 ID 수집
            Map<String, Set<String>> subTabIds = new LinkedHashMap<>();
            for (var entry : cat.subTabs().entrySet()) {
                String sc = entry.getKey();
                String subName = entry.getValue();
                Set<String> ids = collectPostIds(cat.baseUrl() + "?sc=" + sc);
                subTabIds.put(subName, ids);
                System.out.println("  " + subName + ": " + ids.size() + "개");
            }

            // 3. 각 공지의 카테고리 결정 & 처리
            for (String postId : allIds) {
                String category = determineCategory(cat.name(), postId, subTabIds);
                String noticeUrl = cat.baseUrl() + postId;

                try {
                    boolean ok = processNotice(noticeUrl, postId, category);
                    if (ok) {
                        totalSuccess++;
                        System.out.println("  ✓");
                    } else {
                        totalSkip++;
                        System.out.println("  - 스킵");
                    }
                } catch (Exception e) {
                    totalSkip++;
                    System.out.println("  ✗ " + e.getMessage());
                }
                Thread.sleep(300);
            }
        }

        System.out.println("\n=== 크롤링 완료 ===");
        System.out.println("성공: " + totalSuccess + " / 스킵: " + totalSkip);
    }

    private String determineCategory(String mainCat, String postId, Map<String, Set<String>> subTabIds) {
        List<String> matchedSubs = new ArrayList<>();
        for (var entry : subTabIds.entrySet()) {
            if (entry.getValue().contains(postId)) {
                matchedSubs.add(entry.getKey());
            }
        }
        // 여러 서브탭에 있거나 아무 서브탭에도 없으면 → 메인 카테고리만
        if (matchedSubs.size() != 1) {
            return mainCat;
        }
        // 하나의 서브탭에만 있으면 → 메인카테고리.서브탭
        return mainCat + "." + matchedSubs.get(0);
    }

    private Set<String> collectPostIds(String baseUrl) throws IOException, InterruptedException {
        Set<String> ids = new LinkedHashSet<>();
        String separator = baseUrl.contains("?") ? "&" : "?";

        for (int pn = 0; pn <= 40; pn++) {
            String pageUrl = pn == 0 ? baseUrl : baseUrl + separator + "pn=" + pn;
            org.jsoup.nodes.Document doc;
            try {
                doc = Jsoup.connect(pageUrl).userAgent("Mozilla/5.0").timeout(10000).get();
            } catch (Exception e) { break; }

            Elements items = doc.select("li.subject a");
            if (items.isEmpty()) break;

            for (Element a : items) {
                String href = a.attr("href");
                if (href.startsWith("./")) {
                    String id = href.substring(2);
                    if (id.contains("?")) id = id.substring(0, id.indexOf("?"));
                    ids.add(id);
                }
            }

            // 날짜 체크: 최근 2년 이전이면 중단 (동적 계산)
            LocalDateTime cutoff = LocalDateTime.now().minusYears(2);
            String cutoffStr = String.format("%02d.%02d", cutoff.getYear() % 100, cutoff.getMonthValue());
            Elements dates = doc.select("li.date");
            if (!dates.isEmpty()) {
                String lastDate = dates.get(dates.size() - 1).text().trim();
                if (lastDate.compareTo(cutoffStr) < 0) break;
            }

            Thread.sleep(200);
        }
        return ids;
    }

    @org.springframework.transaction.annotation.Transactional
    private boolean processNotice(String noticeUrl, String postId, String category) throws Exception {
        // 중복 방지: URL 기준
        if (documentRepository.existsByOriginalUrl(noticeUrl)) {
            System.out.print("[SKIP 중복] " + noticeUrl);
            return false;
        }

        org.jsoup.nodes.Document doc = Jsoup.connect(noticeUrl)
                .userAgent("Mozilla/5.0").timeout(10000).get();

        Element titleEl = doc.selectFirst("td.view-title");
        String title = titleEl != null ? titleEl.text().trim() : "제목 없음";

        Element contentEl = doc.selectFirst("#view-detail-data");
        String bodyText = contentEl != null ? contentEl.text().trim() : "";

        Elements tds = doc.select(".board-view table td");
        String date = tds.size() > 1 ? tds.get(1).text().trim() : "";
        Element authorEl = doc.selectFirst("td.txt-cen");
        String author = authorEl != null ? authorEl.text().trim() : "";

        System.out.print("[" + category + "] " + title.substring(0, Math.min(35, title.length())));

        // 본문 없어도 첨부파일 있으면 처리
        if (bodyText.isEmpty() && contentEl != null && !contentEl.select("img").isEmpty()) {
            bodyText = "(이미지 공지) " + title;
        }

        // 첨부파일 수집 (중복 제거)
        List<String> attachmentUrls = new ArrayList<>();
        Set<String> seenAttIds = new HashSet<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (href.toLowerCase().contains(".pdf") && href.contains("http")) {
                if (!href.startsWith("https")) href = href.replace("http://", "https://");
                String id = href.contains("id=") ? href.substring(href.indexOf("id=")) : href;
                if (seenAttIds.add(id)) attachmentUrls.add(href);
            }
        }

        // 본문도 없고 첨부파일도 없으면 스킵
        if (bodyText.isEmpty() && attachmentUrls.isEmpty()) return false;

        String fullText = title + "\n\n"
                + "작성일: " + date + " | 작성자: " + author + "\n"
                + "카테고리: " + category + "\n\n"
                + bodyText + "\n\n"
                + "─".repeat(40) + "\n"
                + "원문: " + noticeUrl;

        // PDF 생성 (try-with-resources로 폰트 스트림 관리)
        Path bodyPdf = Path.of(OUTPUT_DIR, "body_" + postId + ".pdf");
        try (InputStream fontStream = findKoreanFont();
             PDDocument pdfDoc = new PDDocument()) {
            if (fontStream == null) throw new RuntimeException("폰트 없음");
            PDType0Font font = PDType0Font.load(pdfDoc, fontStream);
            writeTextToPages(pdfDoc, font, fullText);
            pdfDoc.save(bodyPdf.toFile());
        }

        // 첨부파일 병합
        List<Path> attPdfs = new ArrayList<>();
        for (String attUrl : attachmentUrls) {
            Path p = downloadFile(attUrl);
            if (p != null) attPdfs.add(p);
        }

        Path finalPdf = Path.of(OUTPUT_DIR, sanitizeFilename(title) + ".pdf");
        if (attPdfs.isEmpty()) {
            Files.move(bodyPdf, finalPdf, StandardCopyOption.REPLACE_EXISTING);
        } else {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.addSource(bodyPdf.toFile());
            for (Path att : attPdfs) merger.addSource(att.toFile());
            merger.setDestinationFileName(finalPdf.toString());
            merger.mergeDocuments(null);
            Files.deleteIfExists(bodyPdf);
            for (Path att : attPdfs) Files.deleteIfExists(att);
        }

        // 텍스트 추출 & 청킹
        String extractedText;
        try (PDDocument merged = Loader.loadPDF(finalPdf.toFile())) {
            extractedText = new PDFTextStripper().getText(merged);
        }
        // null byte 제거 (PostgreSQL UTF-8 호환)
        extractedText = extractedText.replace("\u0000", "");

        List<String> chunks = chunkText(extractedText);

        // DB 저장
        Document document = Document.builder()
                .title(title)
                .content(extractedText)
                .category(category)
                .source(DocumentSource.SW)
                .originalUrl(noticeUrl)
                .author(author.isEmpty() ? null : author)
                .publishedAt(parseDate(date))
                .status(DocumentStatus.COMPLETED)
                .build();
        documentRepository.save(document);

        for (int i = 0; i < chunks.size(); i++) {
            chunkRepository.save(DocumentChunk.builder()
                    .document(document).content(chunks.get(i))
                    .chunkIndex(i).hasTable(false).build());
        }

        System.out.print(" → " + chunks.size() + "청크");
        return true;
    }

    // ── 유틸 메서드 ──

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split("\\.");
            if (p.length == 3) {
                int y = Integer.parseInt(p[0].trim()); if (y < 100) y += 2000;
                return LocalDateTime.of(y, Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), 0, 0);
            }
        } catch (Exception e) { }
        return null;
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        text = text.strip();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start += CHUNK_SIZE - CHUNK_OVERLAP;
        }
        return chunks;
    }

    private void writeTextToPages(PDDocument doc, PDType0Font font, String text) throws IOException {
        float pw = PDRectangle.A4.getWidth(), ph = PDRectangle.A4.getHeight(), uw = pw - 2 * MARGIN;
        List<String> wrapped = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) { wrapped.add(""); continue; }
            wrapped.addAll(wrapText(line, font, FONT_SIZE, uw));
        }
        int li = 0;
        while (li < wrapped.size()) {
            PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = ph - MARGIN;
                while (li < wrapped.size() && y > MARGIN) {
                    float fs = li == 0 ? TITLE_FONT_SIZE : FONT_SIZE;
                    float ld = li == 0 ? TITLE_FONT_SIZE + 8 : LINE_SPACING;
                    cs.beginText(); cs.setFont(font, fs); cs.newLineAtOffset(MARGIN, y);
                    cs.showText(wrapped.get(li)); cs.endText();
                    y -= ld; li++;
                }
            }
        }
    }

    private List<String> wrapText(String text, PDType0Font font, float fs, float maxW) throws IOException {
        List<String> r = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try { font.encode(String.valueOf(c)); } catch (Exception e) { c = ' '; }
            sb.append(c);
            try {
                if (font.getStringWidth(sb.toString()) / 1000 * fs > maxW) {
                    r.add(sb.substring(0, sb.length() - 1));
                    sb = new StringBuilder(String.valueOf(c));
                }
            } catch (Exception e) { }
        }
        if (!sb.isEmpty()) r.add(sb.toString());
        return r;
    }

    private Path downloadFile(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setConnectTimeout(10000); c.setReadTimeout(30000);
            if (c.getResponseCode() != 200) return null;
            Path f = Files.createTempFile("att_", ".pdf");
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(f)) { in.transferTo(out); }
            return f;
        } catch (Exception e) { return null; }
    }

    private InputStream findKoreanFont() {
        InputStream is = getClass().getResourceAsStream("/fonts/NanumGothic-Regular.ttf");
        if (is != null) return is;
        for (String p : List.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", "C:/Windows/Fonts/malgun.ttf")) {
            File f = new File(p);
            if (f.exists()) try { return new FileInputStream(f); } catch (Exception e) { }
        }
        return null;
    }

    private String sanitizeFilename(String n) {
        return n.replaceAll("[^가-힣a-zA-Z0-9_\\-\\s]", "").trim().replaceAll("\\s+", "_");
    }
}
