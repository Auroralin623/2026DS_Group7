package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import edu.nccu.moviefinder.model.Keyword;
import edu.nccu.moviefinder.model.SearchResult;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 用假的相依元件測試流程,不連網。整合時需要 A、B、D、E 的部分都已完成或由假物件取代。 */

class SearchServiceTest {
    private static final String GOOD = "https://good.example/";
    private static final String WEAK = "https://weak.example/";
    private static final String BROKEN = "https://broken.example/";

    private final List<String> googleCalls = new ArrayList<>();

    private SearchService service(Map<String, String> pages) {
        KeywordExpander expander = new KeywordExpander() {
            @Override
            public List<String> expand(String q) {
                return List.of(q + " movie");
            }

            @Override
            public List<Keyword> deriveKeywords(List<WebPage> topPages, String originalQuery) {
                return List.of(new Keyword("chef", 1));
            }
        };
        GoogleQuery google = new GoogleQuery("key", 20) {
            @Override
            public LinkedHashMap<String, String> query(String q) {
                googleCalls.add(q);
                LinkedHashMap<String, String> results = new LinkedHashMap<>();
               results.put(WEAK, "Weak");

if (q.contains("chef")) {
    results.put(GOOD + "?utm_source=google", "Good");
} else {
    results.put(GOOD, "Good");
}

results.put(BROKEN, "Broken");
                return results;
            }
        };
        SubLinkCrawler crawler = new SubLinkCrawler(5) {
            @Override
            public List<String> getSubLinks(String url) throws IOException {
                if (url.equals(BROKEN)) {
                    throw new IOException("HTTP 500");
                }
                return (url.equals(GOOD)
        || url.equals(GOOD + "?utm_source=google"))
        ? List.of(GOOD + "sub")
        : List.of();
            }
        };
        Ranker ranker = new Ranker() {
            @Override
            public void sort(List<WebTree> trees) {
                trees.sort(Comparator.comparingDouble(WebTree::getTotalScore).reversed());
            }
        };
        return new SearchService(expander, google, crawler, new PagePrefetcher(), ranker) {
            @Override
            protected WebPage newPage(String url, String title) {
                return new WebPage(
        url, title,
        new WordCounter(
                url,
                pages.getOrDefault(normalizeUrl(url), "")
        )
);
            }
        };
    }

    private static Map<String, String> pages() {
        return Map.of(GOOD, "cat cat review chef", GOOD + "sub", "cat chef", WEAK, "cat");
    }

    @Test
    void ranksByScoreAndNumbersFromOne() {
        List<SearchResult> results = service(pages()).search("cat");

        assertEquals(GOOD + "?utm_source=google",
        results.get(0).getUrl());
        assertEquals(1, results.get(0).getRank());
        assertEquals(2, results.get(1).getRank());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void breakdownAddsUpToTheScore() {
        for (SearchResult r : service(pages()).search("cat")) {
            double sum = r.getBreakdown().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(r.getScore(), sum, 1e-9, r.getUrl());
        }
    }

    @Test
    void secondRoundUsesDerivedKeywords() {
        service(pages()).search("cat");

        assertTrue(googleCalls.size() > 1, "expected a second round");
        assertTrue(googleCalls.stream().anyMatch(q -> q.contains("chef")), googleCalls.toString());
    }

    @Test
    void oneBrokenSiteDoesNotFailTheSearch() {
        assertTrue(service(pages()).search("cat").size() >= 2);
    }


    @Test
    void relatedKeywordsReturnsDerivedTermsWithoutNewSearch() {

        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(
                        new MockHttpServletRequest()));

        try {
            SearchService searchService = service(pages());

            searchService.search("cat");

            int callsBefore = googleCalls.size();

            assertEquals(
                    List.of("chef"),
                    searchService.relatedKeywords("cat"));

            assertEquals(
                    callsBefore,
                    googleCalls.size());

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void relatedKeywordsAreIsolatedBetweenRequests() {

        SearchService searchService = service(pages());

        try {
            // 第一個使用者的請求
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(
                            new MockHttpServletRequest()));

            searchService.search("cat");

            assertEquals(
                    List.of("chef"),
                    searchService.relatedKeywords("cat"));

            // 第二個使用者的請求
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(
                            new MockHttpServletRequest()));

            assertTrue(
                    searchService.relatedKeywords("cat").isEmpty());

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

@Test
void removesDuplicateUrlsAcrossRounds() {
    SearchService searchService = service(pages());

    List<SearchResult> results =
            searchService.search("cat");

    assertEquals(2, googleCalls.size());

    assertEquals(3, results.size());

    long goodCount = results.stream()
            .filter(result -> searchService.normalizeUrl(
                    result.getUrl()
            ).equals(GOOD))
            .count();

    assertEquals(1L, goodCount);
}

}
