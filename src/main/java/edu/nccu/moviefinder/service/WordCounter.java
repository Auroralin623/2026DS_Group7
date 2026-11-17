package edu.nccu.moviefinder.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * - 負責抓取網頁內容並計算關鍵字出現次數。
 * - 英文關鍵字採用完整單字比對，中文維持原本比對方式。
 * - 字串搜尋採用 Boyer-Moore 演算法。
 */
public class WordCounter {

    private final String url;
    private String content;

    /**
     * - 用於正常營運：只傳入網址，後續透過 fetchContent() 抓取。
     */
    public WordCounter(String url) {
        this.url = url;
    }

    /**
     * - 用於單元測試：直接傳入網址與測試內文，免去網路連線。
     */
    public WordCounter(String url, String content) {
        this.url = url;
        this.content = content;
    }

    /**
     * - 抓取網頁內容（僅取純文字），並設定逾時和 User-Agent。
     * - 修正：改為回傳 String，以符合其他測試或呼叫端的型別預期。
     */
    public String fetchContent() throws IOException {
        if (content != null) {
            return content;
        }
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(5000)
                .get();
        this.content = doc.text();
        return this.content;
    }

    public String getContent() throws IOException {
        if (content == null) {
            content = fetchContent();
        }
        return content;
    }

    /**
     * - 計算關鍵字出現次數。
     * - 中文維持原本比對方式，英文比對完整單字（利用 ASCII 邊界判斷避免中文緊鄰時被誤判）。
     * - 內部改用 Boyer-Moore 演算法進行字串搜尋。
     */
    public int countKeyword(String keyword) {
        if (content == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }

        // 檢查是否全為英文/數字（粗略判斷是否為英文關鍵字）
        boolean isEnglishKeyword = keyword.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));

        if (!isEnglishKeyword) {
            // 中文比對，改用 Boyer-Moore 演算法計算出現次數
            List<Integer> matches = boyerMooreSearchAll(content, keyword);
            return matches.size();
        } else {
            // 英文完整單字比對，改用 Boyer-Moore 演算法找出位置後再做邊界確認
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

    /**
     * Boyer-Moore 演算法實作（使用 Map 支援中文與所有 Unicode 字元，避免陣列邊界報錯）
     */
    private List<Integer> boyerMooreSearchAll(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) {
            return matches;
        }

        // 壞字元對照表
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
                s += m; // 匹配成功後向右移動字串長度，確保不重複計算
            } else {
                char badCharInText = text.charAt(s + j);
                int badCharShift = badChar.getOrDefault(badCharInText, -1);
                s += Math.max(1, j - badCharShift);
            }
        }
        return matches;
    }

    private boolean isWholeWord(String text, int start, int length) {
        // 檢查前一個字元
        if (start > 0) {
            char prev = text.charAt(start - 1);
            if (isAsciiLetterOrDigit(prev)) {
                return false;
            }
        }
        // 檢查後一個字元
        int end = start + length;
        if (end < text.length()) {
            char next = text.charAt(end);
            if (isAsciiLetterOrDigit(next)) {
                return false;
            }
        }
        return true;
    }

    /**
     * - 只把 ASCII 英文字母和數字當成單字的一部分，避免中文字被誤判為字母邊界。
     */
    private boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}