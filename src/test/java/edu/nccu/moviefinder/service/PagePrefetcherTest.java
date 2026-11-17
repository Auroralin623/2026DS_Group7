package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PagePrefetcherTest {

    private static WebTree tree(WordCounter counter) {
        return new WebTree(new WebNode(new WebPage("https://e.example", "t", counter)));
    }

    @Test
    void fetchesEachPageOnceAndSurvivesFailures() throws IOException {
        AtomicInteger fetches = new AtomicInteger();
        WordCounter good = new WordCounter("https://good.example") {
            @Override
            public String fetchContent() {
                fetches.incrementAndGet();
                return "movie";
            }
        };
        WordCounter broken = new WordCounter("https://broken.example") {
            @Override
            public String fetchContent() throws IOException {
                throw new IOException("HTTP 500");
            }
        };

        new PagePrefetcher().prefetch(List.of(tree(broken), tree(good)));

        assertEquals("movie", good.getContent());
        assertEquals(1, fetches.get());
    }

    @Test
    void fetchesPagesAtTheSameTime() {
        int pages = 3;
        CountDownLatch allStarted = new CountDownLatch(pages);
        AtomicInteger overlapped = new AtomicInteger();
        List<WebTree> trees = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            trees.add(tree(new WordCounter("https://e.example/" + i) {
                @Override
                public String fetchContent() throws IOException {
                    allStarted.countDown();
                    try {
                        // 只有當其他網頁也同時在抓取時,這裡才會在時限內等到。
                        if (allStarted.await(2, TimeUnit.SECONDS)) {
                            overlapped.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                    return "";
                }
            }));
        }

        new PagePrefetcher().prefetch(trees);

        assertEquals(pages, overlapped.get());
    }
}
