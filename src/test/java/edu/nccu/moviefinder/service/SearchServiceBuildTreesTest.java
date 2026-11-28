package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 確認網站樹是同時建立的,而且順序、失敗處理都和一個一個建立時相同。不連網。 */
class SearchServiceBuildTreesTest {

    private SearchService service(SubLinkCrawler crawler) {
        return new SearchService(null, null, crawler, null, null) {
            @Override
            protected WebPage newPage(String url, String title) {
                return new WebPage(url, title, new WordCounter(url, ""));
            }
        };
    }

    private LinkedHashMap<String, String> sites(int count) {
        LinkedHashMap<String, String> results = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) {
            results.put("https://site" + i + ".example/", "Site " + i);
        }
        return results;
    }

    @Test
    void sitesAreCrawledAtTheSameTime() {
        int count = 4;
        CountDownLatch allStarted = new CountDownLatch(count);
        // 每個網站都等到所有網站開始了才回傳。一個接一個做的話,第一個永遠等不到其他人。
        SubLinkCrawler crawler = new SubLinkCrawler(5) {
            @Override
            public List<String> getSubLinks(String url) throws IOException {
                allStarted.countDown();
                try {
                    if (!allStarted.await(2, TimeUnit.SECONDS)) {
                        throw new IOException("the other sites never started");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                return List.of(url + "sub");
            }
        };

        List<WebTree> trees = service(crawler).buildTrees(sites(count));

        assertEquals(count, trees.size());
        for (WebTree tree : trees) {
            assertEquals(1, tree.getRoot().getChildren().size());
        }
    }

    @Test
    void treesKeepTheOrderOfTheSearchResults() {
        // 排在前面的網站比較慢,確認結果不會變成「誰先做完誰排前面」
        SubLinkCrawler crawler = new SubLinkCrawler(5) {
            @Override
            public List<String> getSubLinks(String url) throws IOException {
                if (url.contains("site1")) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return List.of();
            }
        };

        List<WebTree> trees = service(crawler).buildTrees(sites(3));

        assertEquals(
                List.of("https://site1.example/", "https://site2.example/", "https://site3.example/"),
                trees.stream().map(t -> t.getRoot().getWebPage().getUrl()).toList());
    }

    @Test
    void aSiteThatFailsIsSkippedAndOneWithoutSubpagesIsKept() {
        SubLinkCrawler crawler = new SubLinkCrawler(5) {
            @Override
            public List<String> getSubLinks(String url) throws IOException {
                if (url.contains("site1")) {
                    throw new IOException("HTTP 500");
                }
                if (url.contains("site2")) {
                    throw new IllegalStateException("unexpected");
                }
                return List.of(url + "sub");
            }
        };

        List<WebTree> trees = service(crawler).buildTrees(sites(3));

        assertEquals(
                List.of("https://site1.example/", "https://site3.example/"),
                trees.stream().map(t -> t.getRoot().getWebPage().getUrl()).toList());
        assertTrue(trees.get(0).getRoot().getChildren().isEmpty());
        assertEquals(1, trees.get(1).getRoot().getChildren().size());
    }

    @Test
    void noResultsMeansNoTrees() {
        assertTrue(service(new SubLinkCrawler(5)).buildTrees(new LinkedHashMap<>()).isEmpty());
    }
}
