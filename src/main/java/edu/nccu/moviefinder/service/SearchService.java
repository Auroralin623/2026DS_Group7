package edu.nccu.moviefinder.service;
import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.SearchResult;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 串起整個搜尋流程:補字 → 搜尋 → 建樹 → 計分 → 排序 → 找新關鍵字 → 第二輪。
 * 這是 singleton,所以 KeywordList 必須是方法內的區域變數,不可放在欄位。
 */
@Service
public class SearchService {
    public static final int MAX_ROUND = 2;

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
        // TODO(Member C, Stage 3 → 4)
        throw new UnsupportedOperationException("TODO(Member C): SearchService.search");
    }

    /** 跑一輪:對每個查詢字串搜尋,以網址去重後建樹,呼叫 prefetcher.prefetch,再計分、排序。 */
    protected List<WebTree> runRound(List<String> queries, KeywordList kws) {
        // 1. 收集所有查詢的結果，並依 URL 去重
    LinkedHashMap<String, String> results = new LinkedHashMap<>();

    for (String query : queries) {
        try {
            LinkedHashMap<String, String> found = googleQuery.query(query);

            for (var entry : found.entrySet()) {
                results.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (IOException | IllegalStateException e) {
            throw new SearchUnavailableException(
                "Tavily search is unavailable", e);
        }
    }

    // 2. 將每個搜尋結果建立成網站樹
    List<WebTree> trees = new ArrayList<>();

    for (var entry : results.entrySet()) {
    try {
        WebTree tree = buildTree(entry.getKey(), entry.getValue());
        trees.add(tree);
    } catch (RuntimeException e) {
        System.err.println(
            "Failed to build tree for " + entry.getKey()
            + ": " + e.getMessage()
        );
    }
}

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
    public List<String> relatedKeywords(String q) {
        return List.of();
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
