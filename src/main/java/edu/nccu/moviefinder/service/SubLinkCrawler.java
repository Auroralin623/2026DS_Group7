package edu.nccu.moviefinder.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * 負責從網頁中抓取同網域子網頁，限制最多指定數量，並過濾重複、# 錨點、Base Page 以及常見非網頁檔案。
 */
@Service
public class SubLinkCrawler {

    private int maxLinks = 5;
    private final int maxDepth = 1;

    /**
     * 預設建構子，預設最大連結數為 5。
     */
    public SubLinkCrawler() {
        this.maxLinks = 5;
    }

    /**
     * 支援自定義最大連結數的建構子（供單元測試使用，例如 new SubLinkCrawler(5)）。
     */
    public SubLinkCrawler(int maxLinks) {
        this.maxLinks = maxLinks;
    }

    /**
     * 用 Jsoup 抓取 HTML。
     * 宣告 throws IOException，以相容測試檔覆寫時的例外規範。
     */
    public List<String> getSubLinks(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .timeout(4000)
                .get();
        return extractLinks(doc, url);
    }

    /**
     * 讓測試可以直接傳入 HTML 字串與 Base URL 進行解析。
     */
    public List<String> extractLinks(String html, String baseUrl) {
        if (html == null || html.isEmpty()) {
            return new ArrayList<>();
        }
        Document doc = Jsoup.parse(html, baseUrl);
        return extractLinks(doc, baseUrl);
    }

    /**
     * 從 Document 中擷取最多指定數量的同網域子網頁，去除重複、# 錨點、Base Page 以及非網頁檔案。
     */
    public List<String> extractLinks(Document doc, String baseUrl) {
        List<String> validLinks = new ArrayList<>();
        if (doc == null || baseUrl == null) {
            return validLinks;
        }

        Set<String> seenUrls = new HashSet<>();
        Elements links = doc.select("a[href]");

        // 解析基本網域 (例如 https://example.com)
        java.net.URI baseUri;
        try {
            baseUri = new java.net.URI(baseUrl);
        } catch (Exception e) {
            return validLinks;
        }
        String baseHost = baseUri.getHost();

        // 統一正規化 baseUrl（去除結尾斜線，方便後續比對）
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        for (Element link : links) {
            if (validLinks.size() >= maxLinks) {
                break;
            }

            String absUrl = link.absUrl("href");
            if (absUrl.isEmpty()) {
                continue;
            }

            // 去除 # 錨點
            int hashIdx = absUrl.indexOf('#');
            if (hashIdx != -1) {
                absUrl = absUrl.substring(0, hashIdx);
            }

            // 檢查是否為空或已經看過
            if (absUrl.isEmpty() || seenUrls.contains(absUrl)) {
                continue;
            }

            // 檢查是否為起始網頁本身 (Base Page)，避免把首頁自己抓進子連結
            String normalizedAbs = absUrl.endsWith("/") ? absUrl.substring(0, absUrl.length() - 1) : absUrl;
            if (normalizedAbs.equals(normalizedBase)) {
                continue;
            }

            // 檢查副檔名，過濾圖片、PDF 等非網頁檔案
            String lowerUrl = absUrl.toLowerCase();
            if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|svg|pdf|zip|mp4|css|js)(\\?.*)?$")) {
                continue;
            }

            // 過濾登入、註冊、隱私權條款等無電影內容的功能頁面
            if (lowerUrl.matches(".*(/login|/signin|/signup|/register|/privacy|/terms|/cookie|/account)(/.*|\\?.*)?$")) {
                continue;
            }

            // 檢查是否同網域
            try {
                java.net.URI targetUri = new java.net.URI(absUrl);
                String targetHost = targetUri.getHost();
                if (targetHost != null && targetHost.equals(baseHost)) {
                    seenUrls.add(absUrl);
                    validLinks.add(absUrl);
                }
            } catch (Exception e) {
                // 忽略解析失敗的無效網址
            }
        }

        return validLinks;
    }
}