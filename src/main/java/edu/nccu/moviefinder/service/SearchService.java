package edu.nccu.moviefinder.service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import edu.nccu.moviefinder.model.Keyword;
import java.util.HashMap;
import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.SearchResult;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * 串起整個搜尋流程:補字 → 搜尋 → 建樹 → 計分 → 排序 → 找新關鍵字 → 第二輪。
 * 這是 singleton,所以 KeywordList 必須是方法內的區域變數,不可放在欄位。
 */
@Service
public class SearchService {
    public static final int MAX_ROUND = 2;
    /** 同時找子網頁的網站數。 */
    private static final int BUILD_THREADS = 12;
    /** 一輪中找子網頁的時間上限。 */
    private static final long BUILD_TIMEOUT_SECONDS = 8;

    protected final KeywordExpander expander;
    protected final GoogleQuery googleQuery;
    protected final SubLinkCrawler crawler;
    protected final PagePrefetcher prefetcher;
    protected final Ranker ranker;

    public SearchService(KeywordExpander expander, GoogleQuery googleQuery,
                         SubLinkCrawler crawler, PagePrefetcher prefetcher, Ranker ranker) {
        this.expander = expander;
        this.googleQuery = googleQuery;
        this.crawler = crawler;
        this.prefetcher = prefetcher;
        this.ranker = ranker;
    }

    /**
     * 回傳依分數排序的結果,rank 從 1 開始,最多跑 MAX_ROUND 輪。
     * 單一網站失敗時略過該網站,不可讓整次搜尋失敗。
     * 搜尋引擎整個無法使用時(GoogleQuery 丟出 IOException 或 IllegalStateException),
     * 丟出 SearchUnavailableException,讓介面顯示錯誤訊息而不是「沒有找到結果」。
     */
    
public ArrayList<SearchResult> search(String q) {

    ArrayList<SearchResult> results = new ArrayList<>();

    if (q == null || q.isBlank()) {
        return results;
    }

    q = q.trim();

    // 1. 建立本次搜尋專用的關鍵字清單
    KeywordList kws = KeywordList.defaultsFor(q);

    // 2. 擴充原始查詢
    List<String> queries = expander.expand(q);

    // 3. 執行第一輪搜尋
    List<WebTree> trees = runRound(queries, kws);

    // 4. 取得排名前五名的首頁
    List<WebPage> topPages = new ArrayList<>();

    for (int i = 0; i < Math.min(5, trees.size()); i++) {
        topPages.add(trees.get(i).getRoot().getWebPage());
    }

    // 5. 使用 TF-IDF 找出新關鍵字
    List<Keyword> derived = expander.deriveKeywords(topPages, q);
saveRelatedKeywords(q, derived);
    // 6. 有新關鍵字才執行第二輪
    if (MAX_ROUND > 1 && !derived.isEmpty()) {

        StringBuilder expandedQuery = new StringBuilder(q);

        for (Keyword keyword : derived) {
            kws.add(keyword);
            expandedQuery.append(" ").append(keyword.getName());
        }

        List<String> secondQueries =
                expander.expand(expandedQuery.toString());

        List<WebTree> secondTrees = runRound(secondQueries, kws);

      
// 7. 合併兩輪結果，依標準化網址去重
LinkedHashMap<String, WebTree> merged =
        new LinkedHashMap<>();

for (WebTree tree : trees) {
    String url = tree.getRoot().getWebPage().getUrl();
    merged.put(normalizeUrl(url), tree);
}

for (WebTree tree : secondTrees) {
    String url = tree.getRoot().getWebPage().getUrl();
    merged.put(normalizeUrl(url), tree);
}

trees = new ArrayList<>(merged.values());


        // 8. 統一使用更新後的關鍵字重新計分
        List<WebTree> rescoredTrees = new ArrayList<>();

        for (WebTree tree : trees) {
            try {
                tree.setPostOrderScore(kws);
                rescoredTrees.add(tree);
            } catch (RuntimeException e) {
                System.err.println(
                        "Failed to rescore tree: "
                        + tree.getRoot().getWebPage().getUrl()
                );
            }
        }

        trees = rescoredTrees;

        // 9. 使用 QuickSort 重新排序
        ranker.sort(trees);
    }

    // 10. 將網站樹轉換成 SearchResult
    for (int i = 0; i < trees.size(); i++) {

        WebTree tree = trees.get(i);
        WebPage page = tree.getRoot().getWebPage();

        results.add(new SearchResult(
                i + 1,
                page.getTitle(),
                page.getUrl(),
                tree.getTotalScore(),
                calculateBreakdown(tree, kws),
                tree
        ));
    }

    return results;
}


    /** 跑一輪:對每個查詢字串搜尋,以網址去重後建樹,呼叫 prefetcher.prefetch,再計分、排序。 */
    protected List<WebTree> runRound(List<String> queries, KeywordList kws) {
        // 1. 收集所有查詢的結果，並依 URL 去重
    LinkedHashMap<String, String> results = new LinkedHashMap<>();
    java.util.HashSet<String> seenUrls = new java.util.HashSet<>();

    for (String query : queries) {
        try {
            LinkedHashMap<String, String> found = googleQuery.query(query);

            for (var entry : found.entrySet()) {
    String normalizedUrl = normalizeUrl(entry.getKey());

    if (seenUrls.add(normalizedUrl)) {
        results.put(entry.getKey(), entry.getValue());
    }
}
        } catch (IOException | IllegalStateException e) {
            throw new SearchUnavailableException(
                "Tavily search is unavailable", e);
        }
    }

    // 2. 將每個搜尋結果建立成網站樹(同時進行,順序維持和搜尋結果相同)
    List<WebTree> trees = buildTrees(results);

    // 3. 預先抓取所有網頁內容
    prefetcher.prefetch(trees);

    // 4. 計算每棵網站樹的分數
    List<WebTree> scoredTrees = new ArrayList<>();

   for (WebTree tree : trees) {
    try {
        tree.setPostOrderScore(kws);
        scoredTrees.add(tree);
    } catch (RuntimeException e) {
        System.err.println(
            "Failed to score tree: "
            + tree.getRoot().getWebPage().getUrl()
            + ": " + e.getMessage()
        );
    }
}

    // 5. 使用 QuickSort 排序
    ranker.sort(scoredTrees);

    return scoredTrees;
    }

/** 移除不影響網頁內容的 URL 參數，作為去重依據。 */
protected String normalizeUrl(String url) {
    int fragmentIndex = url.indexOf('#');

    if (fragmentIndex >= 0) {
        url = url.substring(0, fragmentIndex);
    }

    int queryIndex = url.indexOf('?');

    if (queryIndex < 0) {
        return url;
    }

    String base = url.substring(0, queryIndex);
    String query = url.substring(queryIndex + 1);

    List<String> keptParams = new ArrayList<>();

    for (String param : query.split("&")) {
        String key = param.split("=", 2)[0]
                .toLowerCase(java.util.Locale.ROOT);

        if (key.startsWith("utm_")
                || key.equals("fbclid")
                || key.equals("gclid")) {
            continue;
        }

        if (base.startsWith("https://www.businessinsider.com/")
                && key.equals("op")) {
            continue;
        }

        if (!param.isEmpty()) {
            keptParams.add(param);
        }
    }

    if (keptParams.isEmpty()) {
        return base;
    }

    return base + "?" + String.join("&", keptParams);
}

    /**
     * 同時建立所有網站樹。buildTree 要連到每個網站找子網頁,一個接一個做的話,
     * 三十個網站就要等三十次,所以改用執行緒池一起做。
     * 回傳的順序和 results 相同。超過時間上限還沒找完子網頁的網站,保留只有根節點的樹;
     * 建樹失敗的網站略過。
     */
    protected List<WebTree> buildTrees(LinkedHashMap<String, String> results) {
        List<WebTree> trees = new ArrayList<>();
        if (results.isEmpty()) {
            return trees;
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(results.entrySet());
        List<Callable<WebTree>> tasks = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries) {
            tasks.add(() -> buildTree(entry.getKey(), entry.getValue()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(BUILD_THREADS, tasks.size()));
        try {
            List<Future<WebTree>> futures = executor.invokeAll(
                    tasks, BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (int i = 0; i < futures.size(); i++) {
                String url = entries.get(i).getKey();
                String title = entries.get(i).getValue();
                try {
                    trees.add(futures.get(i).get());
                } catch (CancellationException e) {
                    // 來不及找完子網頁:仍保留這個網站,只是沒有子網頁
                    trees.add(new WebTree(new WebNode(newPage(url, title))));
                } catch (ExecutionException e) {
                    System.err.println(
                            "Failed to build tree for " + url
                            + ": " + e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        return trees;
    }

    /** 把一個網站建成 WebTree(根節點 + 最多 maxLinks 個子節點)。 */
    protected WebTree buildTree(String url, String title) {
      // 1. 建立首頁，並包成根節點
    WebPage rootPage = newPage(url, title);
    WebNode root = new WebNode(rootPage);

    // 2. 建立網站樹
    WebTree tree = new WebTree(root);

    try {
        // 3. 呼叫 B 的爬蟲，取得子網頁網址
        List<String> links = crawler.getSubLinks(url);

        // 4. 把子網頁加入根節點
        for (String link : links) {
            WebPage childPage = newPage(link, link);
            WebNode childNode = new WebNode(childPage);
            root.addChild(childNode);
        }

    } catch (IOException e) {
        // 找不到子網頁時，保留只有根節點的樹
    }

    // 5. 回傳整棵樹
    return tree;
    }
    /**
     * 和查詢相關的關鍵字,顯示在結果頁的 Related Keywords。目前回傳空清單,頁面會隱藏這個區塊。
     * TODO(Member C、D): 接上 KeywordExpander.deriveKeywords 的結果。
     * 注意不要為此重跑一次搜尋,可以在 search 時保存結果,或改成由 search 一起回傳。
     */


/** 計算整棵網站樹中每個關鍵字的分數貢獻。 */
protected HashMap<String, Double> calculateBreakdown(
        WebTree tree, KeywordList kws) {

    HashMap<String, Double> breakdown = new HashMap<>();

    accumulateBreakdown(tree.getRoot(), kws, 1.0, breakdown);

    return breakdown;
}

/** 遞迴累加每個節點的關鍵字分數。 */
private void accumulateBreakdown(
        WebNode node,
        KeywordList kws,
        double discount,
        HashMap<String, Double> breakdown) {

    WebPage page = node.getWebPage();

    for (Keyword keyword : kws.getList()) {
        String name = keyword.getName();

        int count = page.getCounts().getOrDefault(name, 0);

        double contribution =
                count * keyword.getWeight() * discount;

        breakdown.merge(name, contribution, Double::sum);
    }

    for (WebNode child : node.getChildren()) {
        accumulateBreakdown(
                child,
                kws,
                discount * WebNode.CHILD_DISCOUNT,
                breakdown);
    }
}


   public List<String> relatedKeywords(String q) {

    RequestAttributes request =
            RequestContextHolder.getRequestAttributes();

    if (request == null || q == null) {
        return List.of();
    }

    Object value = request.getAttribute(
            "movieFinder.related." + q.trim(),
            RequestAttributes.SCOPE_REQUEST
    );

    List<String> names = new ArrayList<>();

    if (value instanceof List<?> items) {
        for (Object item : items) {
            if (item instanceof String name) {
                names.add(name);
            }
        }
    }

    return names;
}
private void saveRelatedKeywords(
        String q, List<Keyword> derived) {

    List<String> names = new ArrayList<>();

    for (Keyword keyword : derived) {
        names.add(keyword.getName());
    }

    RequestAttributes request =
            RequestContextHolder.getRequestAttributes();

    if (request != null) {
        request.setAttribute(
                "movieFinder.related." + q.trim(),
                List.copyOf(names),
                RequestAttributes.SCOPE_REQUEST
        );
    }
}
    /** 所有 WebPage 都由此建立,測試時可覆寫成不連網的版本。 */
    protected WebPage newPage(String url, String title) {
        return new WebPage(url, title);
    }

    /** 未經重新排序的搜尋引擎結果(網址 → 標題),給 compare.html 使用。 */
    public LinkedHashMap<String, String> googleResults(String q) throws IOException {
        return googleQuery.query(q);
    }
}
