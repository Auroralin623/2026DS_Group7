
package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.*;

import edu.nccu.moviefinder.model.Keyword;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchServiceRoundTest {

    private SearchService createService() {

        // 模擬 Tavily：不需要 API Key
        GoogleQuery google = new GoogleQuery("fake-key", 20) {
            @Override
            public LinkedHashMap<String, String> query(String q) {
                LinkedHashMap<String, String> results =
                        new LinkedHashMap<>();

                results.put("https://good.example/", "Good");
                results.put("https://weak.example/", "Weak");
                results.put("http://good.example/", "Good HTTP Duplicate");
                results.put(
    "https://good.example/?utm_source=google",
    "Good Duplicate"
);
                return results;
            }
        };

        // 模擬 B 的爬蟲
        SubLinkCrawler crawler = new SubLinkCrawler(5) {
            @Override
            public List<String> getSubLinks(String url)
                    throws IOException {

                if (url.equals("https://good.example/")) {
                    return List.of("https://good.example/sub");
                }
                return List.of();
            }
        };

        return new SearchService(
                new KeywordExpander(),
                google,
                crawler,
                new PagePrefetcher(),
                new Ranker()
        ) {
            // 模擬網頁內容，不連網
            @Override
            protected WebPage newPage(String url, String title) {
                String content = switch (url) {
                    case "https://good.example/" ->
                            "cat cat cat";
                    case "https://good.example/sub" ->
                            "cat cat";
                    default -> "";
                };

                return new WebPage(
                        url, title,
                        new WordCounter(url, content)
                );
            }
        };
    }

    @Test
    void buildTreeHasRootAndChild() {
        SearchService service = createService();

        WebTree tree = service.buildTree(
                "https://good.example/", "Good"
        );

        assertEquals(
                "https://good.example/",
                tree.getRoot().getWebPage().getUrl()
        );

        assertEquals(1, tree.getRoot().getChildren().size());
    }

    @Test
    void runRoundSortsByScore() {
        SearchService service = createService();

        KeywordList kws = new KeywordList();
        kws.add(new Keyword("cat", 5.0));

        List<WebTree> results = service.runRound(
                List.of("cat movie"), kws
        );

        assertEquals(2, results.size());

        assertEquals(
                "https://good.example/",
                results.get(0).getRoot().getWebPage().getUrl()
        );

        assertTrue(
                results.get(0).getTotalScore() >
                results.get(1).getTotalScore()
        );
    }

    @Test
    void normalizeUrlRemovesTrackingParams() {
        SearchService service = createService();

        assertEquals(
                "https://example.com/movie",
                service.normalizeUrl(
                    "https://example.com/movie?utm_source=google&fbclid=123"
                )
        );
    }

    @Test
    void normalizeUrlRemovesBusinessInsiderOp() {
        SearchService service = createService();

        String url = "https://www.businessinsider.com/article";

        assertEquals(
                url,
                service.normalizeUrl(url + "?op=1")
        );
    }

    @Test
    void normalizeUrlPreservesYoutubeVideoId() {
        SearchService service = createService();

        assertNotEquals(
                service.normalizeUrl(
                    "https://www.youtube.com/watch?v=AAA"
                ),
                service.normalizeUrl(
                    "https://www.youtube.com/watch?v=BBB"
                )
        );
    }

    @Test
    void normalizeUrlKeepsImportantParams() {
        SearchService service = createService();

        assertEquals(
                "https://www.youtube.com/watch?v=AAA",
                service.normalizeUrl(
                    "https://www.youtube.com/watch?v=AAA&utm_source=google"
                )
        );
    }

@Test
void runRoundRemovesDuplicateUrls() {
    SearchService service = createService();

    KeywordList kws = new KeywordList();
    kws.add(new Keyword("cat", 5.0));

    List<WebTree> results = service.runRound(
            List.of("cat movie", "cat film"), kws
    );

    assertEquals(2, results.size());

    long goodCount = results.stream()
            .filter(tree -> service.normalizeUrl(
                    tree.getRoot().getWebPage().getUrl()
            ).equals("https://good.example/"))
            .count();

    assertEquals(1L, goodCount);
}
@Test
void normalizeUrlTreatsHttpAndHttpsAsSame() {
    SearchService service = createService();

    assertEquals(
            service.normalizeUrl("http://example.com/movie"),
            service.normalizeUrl("https://example.com/movie")
    );
}

@Test
void normalizeUrlHandlesProtocolAndTrackingParams() {
    SearchService service = createService();

    assertEquals(
            "https://example.com/movie",
            service.normalizeUrl(
                    "http://example.com/movie?utm_source=google&fbclid=123"
            )
    );
}

@Test
void normalizeUrlPreservesDifferentPaths() {
    SearchService service = createService();

    assertNotEquals(
            service.normalizeUrl("http://example.com/movie1"),
            service.normalizeUrl("https://example.com/movie2")
    );
}

}
