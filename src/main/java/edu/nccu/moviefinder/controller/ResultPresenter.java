package edu.nccu.moviefinder.controller;

import edu.nccu.moviefinder.model.SearchResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把搜尋結果整理成頁面要顯示的資料。只用網址和標題判斷,不需要額外的 API。
 */
public final class ResultPresenter {

    /** 判斷「搜尋某一部電影」時檢查的結果數。 */
    static final int TOP_N = 5;
    /** 前 TOP_N 筆中,至少這麼多筆標題以搜尋字詞開頭,就顯示 Top match。 */
    static final int NARROW_MIN_MATCHES = 2;
    static final int MAX_SOURCES = 6;
    /** 一開始顯示的結果筆數,其餘按「顯示全部」才展開。和 style.css 的 .is-collapsed 規則一致。 */
    public static final int VISIBLE_RESULTS = 10;
    /** 分數顯示到小數一位,絕對值小於這個數字會顯示成 0.0 或 -0.0,所以不列出。 */
    private static final double SHOWN_MIN = 0.05;

    private static final Set<String> GENERIC_WORDS = Set.of("movie", "movies", "film", "films");

    /** 網域 → 顯示名稱。順序不重要,子網域也算(例如 en.wikipedia.org)。 */
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("wikipedia.org", "Wikipedia");
        SOURCES.put("imdb.com", "IMDb");
        SOURCES.put("rottentomatoes.com", "Rotten Tomatoes");
        SOURCES.put("metacritic.com", "Metacritic");
        SOURCES.put("letterboxd.com", "Letterboxd");
        SOURCES.put("themoviedb.org", "TMDB");
        SOURCES.put("reddit.com", "Reddit");
        SOURCES.put("youtube.com", "YouTube");
        SOURCES.put("netflix.com", "Netflix");
        SOURCES.put("disney.com", "Disney");
        SOURCES.put("commonsensemedia.org", "Common Sense Media");
    }

    public record Source(String label, String url) {
    }

    public record Contribution(String keyword, double value, int percent) {
    }

    private ResultPresenter() {
    }

    /** 網址的主機名稱,去掉 www.,無法解析時回傳空字串。 */
    public static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 知名電影相關網站的名稱,不認得時回傳 null。 */
    public static String sourceLabel(String url) {
        String host = host(url);
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            if (host.equals(source.getKey()) || host.endsWith("." + source.getKey())) {
                return source.getValue();
            }
        }
        return null;
    }

    /** 去掉 movie、film 這類通用字後的搜尋字詞,例如 "dog movie" → "dog"。 */
    static String coreQuery(String q) {
        List<String> words = new ArrayList<>();
        for (String word : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty() && !GENERIC_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words.isEmpty() ? q.trim().toLowerCase(Locale.ROOT) : String.join(" ", words);
    }

    /** 前 TOP_N 筆中,標題以搜尋字詞開頭的筆數。 */
    public static int titleMatches(String q, List<SearchResult> results) {
        String core = coreQuery(q);
        if (core.isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (SearchResult r : results.subList(0, Math.min(TOP_N, results.size()))) {
            if (r.getTitle() != null && r.getTitle().toLowerCase(Locale.ROOT).startsWith(core)) {
                matches++;
            }
        }
        return matches;
    }

    /** 是否像在搜尋某一部電影(而不是廣泛的主題)。是的話,第一名會放大成 Top match。 */
    public static boolean isNarrow(String q, List<SearchResult> results) {
        return titleMatches(q, results) >= NARROW_MIN_MATCHES;
    }

    /** 第一名以外、來自知名電影網站的結果,每個網站只列一次。 */
    public static List<Source> relatedSources(List<SearchResult> results) {
        List<Source> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchResult r : results.subList(Math.min(1, results.size()), results.size())) {
            String label = sourceLabel(r.getUrl());
            if (label != null && seen.add(label)) {
                sources.add(new Source(label, r.getUrl()));
                if (sources.size() == MAX_SOURCES) {
                    break;
                }
            }
        }
        return sources;
    }

    /**
     * 各關鍵字的分數貢獻,由高到低排序,percent 是相對於最大貢獻的比例(畫長條圖用)。
     * 沒有貢獻(顯示出來會是 0.0)的關鍵字不列出。
     */
    public static List<Contribution> contributions(SearchResult r) {
        if (r == null || r.getBreakdown() == null || r.getBreakdown().isEmpty()) {
            return List.of();
        }
        double max = r.getBreakdown().values().stream().mapToDouble(Math::abs).max().orElse(0);
        List<Contribution> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : r.getBreakdown().entrySet()) {
            if (Math.abs(e.getValue()) < SHOWN_MIN) {
                continue;
            }
            int percent = max == 0 ? 0 : (int) Math.round(Math.abs(e.getValue()) / max * 100);
            list.add(new Contribution(e.getKey(), e.getValue(), percent));
        }
        list.sort(Comparator.comparingDouble(Contribution::value).reversed());
        return list;
    }

    /** 名次 → 該筆結果的分數明細,給結果頁每一筆的「分數明細」使用。 */
    public static Map<Integer, List<Contribution>> details(List<SearchResult> results) {
        Map<Integer, List<Contribution>> details = new HashMap<>();
        for (SearchResult r : results) {
            details.put(r.getRank(), contributions(r));
        }
        return details;
    }

    /** 網址 → 在 Tavily 原始結果中的排名(從 1 開始),給比較頁計算名次變化。 */
    public static Map<String, Integer> ranks(Map<String, String> tavily) {
        Map<String, Integer> ranks = new HashMap<>();
        int rank = 1;
        for (String url : tavily.keySet()) {
            ranks.put(url, rank++);
        }
        return ranks;
    }
}
