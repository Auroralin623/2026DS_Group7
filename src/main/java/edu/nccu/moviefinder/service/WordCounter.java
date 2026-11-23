package edu.nccu.moviefinder.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 負責抓取網頁內容並計算關鍵字出現次數。
 * 英文關鍵字採用完整單字比對，中文維持原本比對方式。
 * 字串搜尋採用 Boyer-Moore 演算法，並支援跨輪內容快取與失敗網址不重試。
 */
public class WordCounter {

    private static final int MAX_CACHE_SIZE = 500;
    private static final ConcurrentHashMap<String, String> CONTENT_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_URLS = ConcurrentHashMap.newKeySet();

    private final String url;
    private volatile String content;

    public WordCounter(String url) {
        this.url = url;
    }

    public WordCounter(String url, String content) {
        this.url = url;
        this.content = content;
    }

    /** 供測試或需要重置狀態時清除快取 */
    public static void clearCache() {
        CONTENT_CACHE.clear();
        FAILED_URLS.clear();
    }

    public String fetchContent() throws IOException {
        if (content != null) {
            return content;
        }
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(4000)
                .get();
        this.content = doc.text();
        return this.content;
    }

    public String getContent() throws IOException {
        if (content != null) {
            return content;
        }

        // 僅對正式的 WordCounter 實例啟用跨輪 URL 快取（避免影響測試中的匿名覆寫子類別）
        boolean useSharedCache = (this.getClass() == WordCounter.class) && (url != null && !url.isEmpty());

        if (useSharedCache) {
            if (FAILED_URLS.contains(url)) {
                throw new IOException("Previously failed URL (skipped retry): " + url);
            }
            String cached = CONTENT_CACHE.get(url);
            if (cached != null) {
                this.content = cached;
                return cached;
            }
        }

        try {
            String fetched = fetchContent();
            this.content = fetched;
            if (useSharedCache && fetched != null) {
                if (CONTENT_CACHE.size() >= MAX_CACHE_SIZE) {
                    CONTENT_CACHE.clear();
                }
                CONTENT_CACHE.put(url, fetched);
            }
            return fetched;
        } catch (IOException e) {
            if (useSharedCache) {
                if (FAILED_URLS.size() >= MAX_CACHE_SIZE) {
                    FAILED_URLS.clear();
                }
                FAILED_URLS.add(url);
            }
            throw e;
        }
    }

    public int countKeyword(String keyword) {
        if (content == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }

        boolean isEnglishKeyword = keyword.chars()
                .allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));

        if (!isEnglishKeyword) {
            List<Integer> matches = boyerMooreSearchAll(content, keyword);
            return matches.size();
        } else {
            int count = 0;
            String lowerContent = content.toLowerCase();
            String lowerKeyword = keyword.toLowerCase();
            int kwLen = keyword.length();

            List<Integer> matches = boyerMooreSearchAll(lowerContent, lowerKeyword);
            for (int idx : matches) {
                if (isWholeWord(lowerContent, idx, kwLen)) {
                    count++;
                }
            }
            return count;
        }
    }

    private List<Integer> boyerMooreSearchAll(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) {
            return matches;
        }

        Map<Character, Integer> badChar = new HashMap<>();
        for (int i = 0; i < m; i++) {
            badChar.put(pattern.charAt(i), i);
        }

        int s = 0;
        while (s <= (n - m)) {
            int j = m - 1;
            while (j >= 0 && pattern.charAt(j) == text.charAt(s + j)) {
                j--;
            }
            if (j < 0) {
                matches.add(s);
                s += m;
            } else {
                char badCharInText = text.charAt(s + j);
                int badCharShift = badChar.getOrDefault(badCharInText, -1);
                s += Math.max(1, j - badCharShift);
            }
        }
        return matches;
    }

    private boolean isWholeWord(String text, int start, int length) {
        if (start > 0 && isAsciiLetterOrDigit(text.charAt(start - 1))) {
            return false;
        }
        int end = start + length;
        if (end < text.length() && isAsciiLetterOrDigit(text.charAt(end))) {
            return false;
        }
        return true;
    }

    private boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}