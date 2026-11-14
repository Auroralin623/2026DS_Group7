package edu.nccu.moviefinder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.nccu.moviefinder.service.WordCounter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebTreeTest {

    private static KeywordList keywords() {
        KeywordList kws = new KeywordList();
        kws.add(new Keyword("movie", 1));
        return kws;
    }

    /** 內容含 hits 個 "movie",在 keywords() 下分數就是 hits。 */
    private static WebNode node(String url, int hits) {
        String content = "movie ".repeat(hits);
        return new WebNode(new WebPage(url, url, new WordCounter(url, content)));
    }

    @Test
    void rootAddsDiscountedChildScores() {
        WebNode root = node("https://a.example/", 10);
        root.addChild(node("https://a.example/1", 4));
        root.addChild(node("https://a.example/2", 2));
        WebTree tree = new WebTree(root);

        tree.setPostOrderScore(keywords());

        assertEquals(10 + 0.5 * (4 + 2), tree.getTotalScore());
    }

    @Test
    void childrenAreScoredBeforeTheirParents() {
        WebNode root = node("https://a.example/", 1);
        WebNode child = node("https://a.example/1", 2);
        child.addChild(node("https://a.example/1/1", 4));
        root.addChild(child);
        WebTree tree = new WebTree(root);

        tree.setPostOrderScore(keywords());

        assertEquals(2 + 0.5 * 4, child.getNodeScore());
        assertEquals(1 + 0.5 * 4, tree.getTotalScore());
    }

    @Test
    void depthCountsEdgesFromTheRoot() {
        WebNode root = node("https://a.example/", 0);
        WebNode child = node("https://a.example/1", 0);
        root.addChild(child);

        assertTrue(root.isRoot());
        assertEquals(0, root.getDepth());
        assertEquals(1, child.getDepth());
    }

    @Test
    void aFailedPageScoresZeroWithoutStoppingTheTree() {
        WordCounter broken = new WordCounter("https://a.example/broken") {
            @Override
            protected String fetchContent() throws IOException {
                throw new IOException("HTTP 404");
            }
        };
        WebNode root = node("https://a.example/", 6);
        root.addChild(new WebNode(new WebPage("https://a.example/broken", "broken", broken)));
        WebTree tree = new WebTree(root);

        tree.setPostOrderScore(keywords());

        assertEquals(6, tree.getTotalScore());
    }

    @Test
    void eulerPrintShowsIndentedNodesWithCounts() {
        WebNode root = node("https://a.example/", 3);
        root.addChild(node("https://a.example/1", 1));
        WebTree tree = new WebTree(root);
        tree.setPostOrderScore(keywords());

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            tree.eulerPrintTree();
        } finally {
            System.setOut(original);
        }

        String[] lines = captured.toString(StandardCharsets.UTF_8).split("\\R");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("https://a.example/"));
        assertTrue(lines[0].contains("score=3.5"));
        assertTrue(lines[0].contains("movie=3"));
        assertTrue(lines[1].startsWith("  https://a.example/1"));
        assertTrue(lines[1].contains("score=1.0"));
        assertTrue(lines[1].contains("movie=1"));
    }
}
