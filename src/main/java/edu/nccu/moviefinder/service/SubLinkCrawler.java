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

    public SubLinkCrawler() {
        this.maxLinks = 5;
    }

    public SubLinkCrawler(int maxLinks) {
        this.maxLinks = maxLinks;
    }

    public List<String> getSubLinks(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .timeout(4000)
                .get();
        return extractLinks(doc, url);
    }

    public List<String> extractLinks(String html, String baseUrl) {
        if (html == null || html.isEmpty()) {
            return new ArrayList<>();
        }
        Document doc = Jsoup.parse(html, baseUrl);
        return extractLinks(doc, baseUrl);
    }

    public List<String> extractLinks(Document doc, String baseUrl) {
        List<String> validLinks = new ArrayList<>();
        if (doc == null || baseUrl == null) {
            return validLinks;
        }

        Set<String> seenUrls = new HashSet<>();
        Elements links = doc.select("a[href]");

        java.net.URI baseUri;
        try {
            baseUri = new java.net.URI(baseUrl);
        } catch (Exception e) {
            return validLinks;
        }
        String baseHost = baseUri.getHost();

        String normalizedBase = stripTrackingParams(baseUrl);
        normalizedBase = normalizedBase.endsWith("/") ? normalizedBase.substring(0, normalizedBase.length() - 1) : normalizedBase;

        for (Element link : links) {
            if (validLinks.size() >= maxLinks) {
                break;
            }

            String absUrl = link.absUrl("href");
            if (absUrl.isEmpty()) {
                continue;
            }

            // 去除 # 錨點與行銷追蹤參數
            int hashIdx = absUrl.indexOf('#');
            if (hashIdx != -1) {
                absUrl = absUrl.substring(0, hashIdx);
            }
            absUrl = stripTrackingParams(absUrl);

            if (absUrl.isEmpty() || seenUrls.contains(absUrl)) {
                continue;
            }

            // 檢查是否為起始網頁本身 (Base Page)
            String normalizedAbs = absUrl.endsWith("/") ? absUrl.substring(0, absUrl.length() - 1) : absUrl;
            if (normalizedAbs.equals(normalizedBase)) {
                continue;
            }

            // 過濾非網頁檔案與無電影內容的功能頁面
            String lowerUrl = absUrl.toLowerCase();
            if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|svg|pdf|zip|mp4|css|js)(\\?.*)?$")) {
                continue;
            }
            if (lowerUrl.matches(".*(/login|/signin|/signup|/register|/privacy|/terms|/cookie|/account)(/.*|\\?.*)?$")) {
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

    /** 移除 utm_、fbclid、gclid 等追蹤參數，保留如 ?id=123 的正常頁面參數 */
    private String stripTrackingParams(String url) {
        int queryIdx = url.indexOf('?');
        if (queryIdx == -1) {
            return url;
        }
        String basePart = url.substring(0, queryIdx);
        String queryPart = url.substring(queryIdx + 1);
        List<String> keptParams = new ArrayList<>();
        for (String param : queryPart.split("&")) {
            String lowerParam = param.toLowerCase();
            if (!lowerParam.startsWith("utm_")
                    && !lowerParam.startsWith("fbclid=")
                    && !lowerParam.startsWith("gclid=")
                    && !lowerParam.startsWith("ref=")
                    && !lowerParam.startsWith("ir=")) {
                keptParams.add(param);
            }
        }
        return keptParams.isEmpty() ? basePart : basePart + "?" + String.join("&", keptParams);
    }
}