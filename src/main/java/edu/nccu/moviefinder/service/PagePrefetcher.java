package edu.nccu.moviefinder.service;

import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * 在計分前平行預抓所有網頁，並透過 WordCounter 快取已抓內容與失敗紀錄。
 */
@Service
public class PagePrefetcher {

    private static final int THREAD_POOL_SIZE = 12;
    private static final long PREFETCH_TIMEOUT_SECONDS = 8;

    /** 單一網頁失敗時略過該網頁,不可中斷其他網頁。 */
    public void prefetch(List<WebTree> trees) {
        if (trees == null || trees.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();

        for (WebTree tree : trees) {
            if (tree != null && tree.getRoot() != null) {
                submitFetchTasks(tree.getRoot(), executor, futures);
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(PREFETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void submitFetchTasks(WebNode node, ExecutorService executor, List<Future<?>> futures) {
        if (node == null) {
            return;
        }

        futures.add(executor.submit(() -> fetch(node)));

        if (node.getChildren() != null) {
            for (WebNode child : node.getChildren()) {
                submitFetchTasks(child, executor, futures);
            }
        }
    }

    private void fetch(WebNode node) {
        if (node == null || node.getWebPage() == null) {
            return;
        }

        try {
            // 第一輪會抓網頁並存入 WordCounter 快取；第二輪會直接從快取回填至新 WebPage
            node.getWebPage().getContent();
        } catch (IOException e) {
            // 失敗的網頁在計分時會以 0 分計，不可中斷其他網頁
        }
    }
}