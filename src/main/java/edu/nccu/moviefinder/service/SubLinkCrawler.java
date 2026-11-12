package edu.nccu.moviefinder.service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 找出一個網頁的子網頁,用來建立兩層的網站樹。 */
@Service
public class SubLinkCrawler {
    private final int maxLinks;
    private final int maxDepth = 1;

    public SubLinkCrawler(@Value("${crawler.max-links:5}") int maxLinks) {
        this.maxLinks = maxLinks;
    }

    /** 抓取 url 的 HTML 後,交給 extractLinks 取出子網頁。 */
    public List<String> getSubLinks(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .timeout(5000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get();
        return extractLinks(doc.html(), url);
    }

    /**
     * - 從 HTML 取出最多 maxLinks 個子網頁的絕對網址,順序依頁面出現順序。
     * - 規則:相對網址要轉成絕對網址、去掉 #fragment、只留同網域的 http(s) 連結、
     * - 用 HashSet 去除重複,且不可包含 baseUrl 自己。
     */
    public List<String> extractLinks(String html, String baseUrl) {
        List<String> result = new ArrayList<>();
        if (html == null || html.isEmpty() || baseUrl == null) {
            return result;
        }

        Document doc = Jsoup.parse(html, baseUrl);
        Elements links = doc.select("a[href]");

        Set<String> seen = new HashSet<>();
        URI baseUri;
        try {
            baseUri = URI.create(baseUrl);
        } catch (Exception e) {
            return result;
        }

        String baseHost = baseUri.getHost();
        String normalizedBase = cleanUrl(baseUrl);

        for (Element link : links) {
            if (result.size() >= maxLinks) {
                break;
            }

            // 取得絕對網址
            String absUrl = link.attr("abs:href");
            if (absUrl == null || absUrl.isEmpty()) {
                continue;
            }

            // 去掉 #fragment 錨點
            int hashIndex = absUrl.indexOf('#');
            if (hashIndex != -1) {
                absUrl = absUrl.substring(0, hashIndex);
            }

            String cleanedUrl = cleanUrl(absUrl);

            // 不可包含 baseUrl 自己
            if (cleanedUrl.equals(normalizedBase)) {
                continue;
            }

            // 檢查是否為 http(s) 且為同網域
            try {
                URI targetUri = URI.create(absUrl);
                String scheme = targetUri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    continue;
                }

                String targetHost = targetUri.getHost();
                if (targetHost == null || !targetHost.equalsIgnoreCase(baseHost)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }

            // 用 HashSet 去除重複，並維持出現順序
            if (seen.add(cleanedUrl)) {
                result.add(absUrl);
            }
        }

        return result;
    }

    /** 輔助方法：統一去除結尾斜線以便進行乾淨的比對 */
    private String cleanUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public int getMaxLinks() {
        return maxLinks;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}