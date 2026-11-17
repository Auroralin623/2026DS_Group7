package edu.nccu.moviefinder.service;

import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.WebTree;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * - 在計分前先把所有網頁抓好。WordCounter 會保存抓到的內容,所以之後計分不會再連網。
 * - 改成用執行緒池平行抓取,並設定整次搜尋的時間上限。
 * - 第二輪搜尋不要重抓已經抓過的網址,也不要重試已經失敗的網址。
 */
@Service
public class PagePrefetcher {

    // 使用執行緒安全的 Set 記錄已成功抓取與已失敗的網頁，避免重複抓取與重試
    private final Set<String> fetchedPages = ConcurrentHashMap.newKeySet();
    private final Set<String> failedPages = ConcurrentHashMap.newKeySet();

    /** 單一網頁失敗時略過該網頁,不可中斷其他網頁。 */
    public void prefetch(List<WebTree> trees) {
        if (trees == null || trees.isEmpty()) {
            return;
        }

        // 建立執行緒池進行平行抓取（可依需求調整執行緒數量，例如 8）
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new java.util.ArrayList<>();

        // 遞迴收集所有節點並提交至執行緒池
        for (WebTree tree : trees) {
            if (tree != null && tree.getRoot() != null) {
                submitFetchTasks(tree.getRoot(), executor, futures);
            }
        }

        // 設定整次搜尋的時間上限（例如 15 秒）
        try {
            executor.shutdown();
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // 超時後強制終止未完成的執行緒
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

        // 提交單一節點抓取任務
        futures.add(executor.submit(() -> {
            fetch(node);
        }));

        // 遞迴處理子節點
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

        // 以 WebPage 的識別字串作為快取 Key
        String pageKey = node.getWebPage().toString();

        // 第二輪搜尋：已抓過或已失敗過的網址直接跳過，不重複抓取、不重試
        if (fetchedPages.contains(pageKey) || failedPages.contains(pageKey)) {
            return;
        }

        try {
            node.getWebPage().getContent();
            fetchedPages.add(pageKey);
        } catch (IOException e) {
            failedPages.add(pageKey);
            // 失敗的網頁在計分時會以 0 分計。
        }
    }
}