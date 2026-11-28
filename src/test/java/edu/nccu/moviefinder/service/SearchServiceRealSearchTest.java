
package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.*;
import edu.nccu.moviefinder.model.SearchResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.io.IOException;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.WebTree;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_SEARCH_TEST",
        matches = "true"
)
class SearchServiceRealSearchTest {

    @Test
    void realFirstRoundSearch() {
        String apiKey = System.getenv("TAVILY_API_KEY");

        assertNotNull(apiKey, "請先設定 TAVILY_API_KEY");
        assertFalse(apiKey.isBlank(), "TAVILY_API_KEY 不可為空");

        SearchService service = new SearchService(
                new KeywordExpander(),
                new GoogleQuery(apiKey, 3),
                new SubLinkCrawler(1),
                new PagePrefetcher(),
                new Ranker()
        );

        String query = "Inception movie";
        KeywordList kws = KeywordList.defaultsFor(query);

        long startTime = System.nanoTime();

        List<WebTree> results = service.runRound(
                List.of(query), kws
        );

        double elapsedSeconds =
                (System.nanoTime() - startTime) / 1_000_000_000.0;

        assertFalse(
                results.isEmpty(),
                "真實搜尋應至少取得一筆可計分結果"
        );

        System.out.println("\n=== REAL SEARCH RESULTS ===");
        System.out.printf(
                "Search time: %.2f seconds%n",
                elapsedSeconds
        );

        for (int i = 0; i < results.size(); i++) {
            WebTree tree = results.get(i);

            System.out.println(
                    (i + 1) + ". "
                    + tree.getRoot().getWebPage().getUrl()
                    + " | Score: " + tree.getTotalScore()
            );

            if (i > 0) {
                assertTrue(
                        results.get(i - 1).getTotalScore()
                                >= tree.getTotalScore(),
                        "搜尋結果應依分數由高到低排序"
                );
            }
        }
    }


    @Test
    void realTwoRoundSearch() {

        String apiKey = System.getenv("TAVILY_API_KEY");

        assertNotNull(apiKey, "請先設定 TAVILY_API_KEY");
        assertFalse(apiKey.isBlank());

        List<String> googleCalls = new ArrayList<>();

        GoogleQuery google = new GoogleQuery(apiKey, 3) {
            @Override
            public java.util.LinkedHashMap<String, String> query(
                    String q) throws IOException {

                googleCalls.add(q);
                System.out.println("Tavily query: " + q);

                return super.query(q);
            }
        };

        SearchService service = new SearchService(
                new KeywordExpander(),
                google,
                new SubLinkCrawler(1),
                new PagePrefetcher(),
                new Ranker()
        );

        String query = "Inception";

        long startTime = System.nanoTime();

        List<SearchResult> results = service.search(query);

        double elapsedSeconds =
                (System.nanoTime() - startTime)
                        / 1_000_000_000.0;

        System.out.println("\n=== TWO ROUND SEARCH RESULTS ===");

        System.out.printf(
                "Search time: %.2f seconds%n",
                elapsedSeconds);

        System.out.println(
                "Tavily calls: " + googleCalls.size());

        System.out.println(
                "Result count: " + results.size());

        assertFalse(results.isEmpty());

        assertEquals(
                6, googleCalls.size(),
                "應完成兩輪搜尋，每輪三組查詢");

        assertTrue(
                googleCalls.get(3).length()
                        > googleCalls.get(0).length(),
                "第二輪應加入新關鍵字");

        HashSet<String> urls = new HashSet<>();

        for (int i = 0; i < results.size(); i++) {

            SearchResult result = results.get(i);

            System.out.println(
                    result.getRank() + ". "
                    + result.getUrl()
                    + " | Score: " + result.getScore());

            System.out.println(
                    "Breakdown: " + result.getBreakdown());

            double sum = result.getBreakdown()
                    .values()
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            assertEquals(result.getScore(), sum, 1e-9);

            assertEquals(i + 1, result.getRank());

            assertTrue(
                    urls.add(result.getUrl()),
                    "不應出現重複網址");

            if (i > 0) {
                assertTrue(
                        results.get(i - 1).getScore()
                                >= result.getScore(),
                        "搜尋結果應依分數由高到低排序");
            }
        }
    }

}

