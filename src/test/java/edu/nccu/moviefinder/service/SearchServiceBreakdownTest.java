
package edu.nccu.moviefinder.service;

import edu.nccu.moviefinder.model.Keyword;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchServiceBreakdownTest {

    @Test
    void breakdownAddsUpToTotalScore() {

        SearchService service =
                new SearchService(null, null, null, null, null);

        KeywordList kws = new KeywordList();
        kws.add(new Keyword("movie", 5));
        kws.add(new Keyword("review", 2));

        WebPage rootPage = new WebPage(
                "https://example.com",
                "Root",
                new WordCounter(
                        "https://example.com",
                        "movie movie review"));

        WebPage childPage = new WebPage(
                "https://example.com/sub",
                "Child",
                new WordCounter(
                        "https://example.com/sub",
                        "movie review review"));

        WebNode root = new WebNode(rootPage);
        root.addChild(new WebNode(childPage));

        WebTree tree = new WebTree(root);
        tree.setPostOrderScore(kws);

        HashMap<String, Double> breakdown =
                service.calculateBreakdown(tree, kws);

        assertEquals(12.5, breakdown.get("movie"), 1e-9);
        assertEquals(4.0, breakdown.get("review"), 1e-9);

        double sum = breakdown.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        assertEquals(16.5, tree.getTotalScore(), 1e-9);
        assertEquals(tree.getTotalScore(), sum, 1e-9);
    }

@Test
void breakdownHandlesNegativeWeightsAndDeepNodes() {

    SearchService service =
            new SearchService(null, null, null, null, null);

    KeywordList kws = new KeywordList();
    kws.add(new Keyword("movie", 5));
    kws.add(new Keyword("ticket", -2));

    WebNode root = new WebNode(new WebPage(
            "https://example.com", "Root",
            new WordCounter("https://example.com",
                    "movie ticket")));

    WebNode child = new WebNode(new WebPage(
            "https://example.com/sub", "Child",
            new WordCounter("https://example.com/sub",
                    "movie movie ticket")));

    WebNode grandchild = new WebNode(new WebPage(
            "https://example.com/deep", "Grandchild",
            new WordCounter("https://example.com/deep",
                    "movie ticket ticket")));

    root.addChild(child);
    child.addChild(grandchild);

    WebTree tree = new WebTree(root);
    tree.setPostOrderScore(kws);

    HashMap<String, Double> breakdown =
            service.calculateBreakdown(tree, kws);

    assertEquals(11.25, breakdown.get("movie"), 1e-9);
    assertEquals(-4.0, breakdown.get("ticket"), 1e-9);

    double sum = breakdown.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();

    assertEquals(7.25, tree.getTotalScore(), 1e-9);
    assertEquals(tree.getTotalScore(), sum, 1e-9);
}

}
