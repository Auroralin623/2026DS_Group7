package edu.nccu.moviefinder.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 取得搜尋引擎的結果。類別名稱沿用 proposal,實際使用 Tavily Search API,
 * 因為 Google 自 2026 年起不再開放新用戶申請搜尋 API(見 HW8)。
 * API 文件:https://docs.tavily.com/documentation/api-reference/endpoint/search
 */
@Service
public class GoogleQuery {
    private static final URI ENDPOINT = URI.create("https://api.tavily.com/search");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** Tavily 允許的 max_results 上限。 */
    private static final int MAX_RESULTS_LIMIT = 20;

/** Cache 最多保留 100 筆搜尋結果。 */
private static final int CACHE_LIMIT = 100;

/** 使用 LRU 機制儲存搜尋結果。 */
private final Map<String, LinkedHashMap<String, String>> cache =
        new LinkedHashMap<String, LinkedHashMap<String, String>>(
                16, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<String, LinkedHashMap<String, String>> eldest) {
                return size() > CACHE_LIMIT;
            }
        };


    private final String apiKey;
    private final int maxResults;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public GoogleQuery(@Value("${tavily.api-key:}") String apiKey,
                       @Value("${tavily.max-results:10}") int maxResults) {
        this.apiKey = apiKey;
        this.maxResults = maxResults;
    }

    /**
     * 回傳「網址 → 標題」,順序就是 Tavily 回傳的順序。
     * apiKey 為空時丟出 IllegalStateException,並在訊息中提示設定 TAVILY_API_KEY。
     * 連線失敗、金鑰錯誤、額度用盡時丟出 IOException,訊息說明原因。
     */


/** 優先使用快取，避免重複呼叫 Tavily API。 */
public LinkedHashMap<String, String> query(String q) throws IOException {

    synchronized (cache) {
        LinkedHashMap<String, String> cached = cache.get(q);

        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
    }

    LinkedHashMap<String, String> results = fetchFromTavily(q);

    synchronized (cache) {
        cache.put(q, new LinkedHashMap<>(results));
    }

    return results;
}

   protected LinkedHashMap<String, String> fetchFromTavily(String q) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Tavily API key is missing. Set the TAVILY_API_KEY environment variable.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", q);
        body.put("max_results", Math.max(1, Math.min(MAX_RESULTS_LIMIT, maxResults)));

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tavily request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException(describeError(response.statusCode()));
        }
        try {
            return parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 解析 Tavily 回傳的 JSON,取 results 陣列中的 url 和 title。
     * 沒有 results 時回傳空的 map;沒有 url 的結果略過;沒有標題時用網址代替;
     * 同一個網址出現兩次時保留排名較前的那一筆。
     * JSON 格式錯誤時丟出 IllegalArgumentException。
     */
    public LinkedHashMap<String, String> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Tavily returned invalid JSON", e);
        }

        LinkedHashMap<String, String> results = new LinkedHashMap<>();
        for (JsonNode item : root.path("results")) {
            String url = item.path("url").asString("").trim();
            if (url.isEmpty()) {
                continue;
            }
            String title = item.path("title").asString("").trim();
            results.putIfAbsent(url, title.isEmpty() ? url : title);
        }
        return results;
    }

    /** 錯誤代碼依 Tavily API 文件。 */
    static String describeError(int status) {
        return switch (status) {
            case 401 -> "Tavily rejected the API key (HTTP 401). Check TAVILY_API_KEY.";
            case 429 -> "Too many Tavily requests (HTTP 429). Wait a moment and try again.";
            case 432, 433 -> "Tavily usage limit reached (HTTP " + status + "). Check the plan on the Tavily dashboard.";
            default -> "Tavily request failed (HTTP " + status + ").";
        };
    }

    protected String getApiKey() {
        return apiKey;
    }

    protected int getMaxResults() {
        return maxResults;
    }
}
