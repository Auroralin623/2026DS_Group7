package edu.nccu.moviefinder.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Arrays;

/**
 * 抓取一個網頁並計算關鍵字次數。
 * 每個網頁一個實例,因此不是 Spring bean。
 *
 * TODO(Member B):
 *  1. (已完成) 改用 Jsoup 抓取並只取內文,排除 HTML 標籤和 script。
 *  2. (已完成) 英文關鍵字要比對完整單字,避免 "cat" 命中 "category"、"AI" 命中 "email"。
 *  3. (已完成) 設定連線逾時與 User-Agent,避免卡住的網站拖住整次搜尋。
 *  4. (已優化) last() 每次呼叫 BoyerMoore 都重建 65536 格陣列,已經改為每個關鍵字只建一次。
 *  5. (已優化) fetchContent 改用 Jsoup 取代字串 O(n²) 串接。
 */
public class WordCounter {
    private final String urlStr;
    private String content;
    private String upperContent;

    public WordCounter(String urlStr) {
        this.urlStr = urlStr;
    }

    /** 測試用:直接給內容,不連網。 */
    public WordCounter(String urlStr, String content) {
        this.urlStr = urlStr;
        this.content = content;
    }

    /** 使用 Jsoup 抓取網頁，設定 User-Agent 與 5 秒逾時，並只取純文字內文[cite: 3, 14, 15]。 */
    protected String fetchContent() throws IOException {
        Document doc = Jsoup.connect(urlStr)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .timeout(5000) // 設定 5 秒逾時，防止慢速網站卡住[cite: 14, 15]
                .get();
        return doc.text(); // 只取得 HTML 的純文字內容，自動排除標籤與 script[cite: 3, 14]
    }

    /** 網頁內容,只抓一次。 */
    public String getContent() throws IOException {
        if (content == null) {
            content = fetchContent();
        }
        return content;
    }

    /** 保留原本的 public 簽名，供外部或測試直接呼叫 */
    public int BoyerMoore(String T, String P, int start) {
        if (P == null || P.isEmpty()) {
            return -1;
        }
        return boyerMooreWithTable(T, P, start, last(P));
    }

    /** 內部使用的 Boyer-Moore，接收預先建好的 last 表避免重複建表 */
    private int boyerMooreWithTable(String T, String P, int start, int[] L) {
        int n = T.length();
        int m = P.length();

        if (m == 0) {
            return -1;
        }

        int i = start + m - 1;
        int j = m - 1;

        while (i <= n - 1) {
            if (T.charAt(i) == P.charAt(j)) {
                if (j == 0) {
                    return i;
                }
                i--;
                j--;
            } else {
                int l = L[T.charAt(i)];
                i += m - Math.min(j, l + 1);
                j = m - 1;
            }
        }

        return -1;
    }

    public int[] last(String P) {
        int[] L = new int[Character.MAX_VALUE + 1];

        Arrays.fill(L, -1);

        for (int i = 0; i < P.length(); i++) {
            L[P.charAt(i)] = i;
        }

        return L;
    }

    /** 判斷關鍵字是否包含英文字母（用來區分英文完整單字比對與中文一般比對） */
    private boolean containsEnglishLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                return true;
            }
        }
        return false;
    }

    /** 檢查比對到的範圍 [start, end) 在前後是否為合法的英文單字邊界 */
    private boolean isWholeWord(String text, int start, int end) {
        if (start > 0) {
            char prev = text.charAt(start - 1);
            if (Character.isLetterOrDigit(prev)) {
                return false;
            }
        }
        if (end < text.length()) {
            char next = text.charAt(end);
            if (Character.isLetterOrDigit(next)) {
                return false;
            }
        }
        return true;
    }

    /** 不分大小寫計算關鍵字次數（英文比對完整單字，中文維持一般比對）[cite: 3, 14]。 */
    public int countKeyword(String keyword) throws IOException {
        if (keyword == null || keyword.isEmpty()) {
            return 0;
        }

        if (upperContent == null) {
            upperContent = getContent().toUpperCase();
        }

        String upperKeyword = keyword.toUpperCase();
        int[] lastTable = last(upperKeyword);
        boolean checkWordBoundary = containsEnglishLetter(upperKeyword);
        int m = upperKeyword.length();

        int retVal = 0;
        int i = 0;
        while (i <= upperContent.length() - m) {
            int j = boyerMooreWithTable(upperContent, upperKeyword, i, lastTable);
            if (j == -1) {
                break;
            }

            if (checkWordBoundary) {
                // 英文關鍵字：必須確認前後都是單字邊界才算數[cite: 3, 14]
                if (isWholeWord(upperContent, j, j + m)) {
                    retVal++;
                    i = j + m;
                } else {
                    i = j + 1;
                }
            } else {
                // 中文或其他非英文字詞：維持原本的比對方式[cite: 3, 14]
                retVal++;
                i = j + 1;
            }
        }
        return retVal;
    }
}