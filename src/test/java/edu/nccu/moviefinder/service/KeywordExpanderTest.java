package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.nccu.moviefinder.model.Keyword;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.WebPage;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordExpanderTest {
    private final KeywordExpander expander = new KeywordExpander();

    private static WebPage page(String content) {
        return new WebPage(
                "https://example.com",
                "t",
                new WordCounter("https://example.com", content));
    }

    @Test
    void expandAddsMovieContextTerms() {
        assertEquals(List.of("cat movie", "cat film", "cat movie review"), expander.expand("cat"));
    }

    @Test
    void derivesTermsSharedByTopPages() {
        List<Keyword> derived = expander.deriveKeywords(List.of(
                page("the chef cooks in the kitchen, chef recipe"),
                page("a chef and the recipe for cooking"),
                page("the chef loves cooking a recipe")), "cooking");

        assertTrue(derived.stream().anyMatch(k -> k.getName().equalsIgnoreCase("chef")));
    }

    @Test
    void excludesStopwordsAndTheOriginalQuery() {
        List<Keyword> derived = expander.deriveKeywords(
                List.of(page("the movie about cooking and the chef"), page("the film about cooking")),
                "cooking");

        for (Keyword keyword : derived) {
            assertFalse(List.of("the", "movie", "film", "cooking", "about")
                    .contains(keyword.getName().toLowerCase()));
        }
    }

    @Test
    void derivedWeightsStayBelowTheUserWeight() {
        List<Keyword> derived = expander.deriveKeywords(
                List.of(page("chef chef chef recipe recipe")), "cooking");

        assertFalse(derived.isEmpty());
        for (Keyword keyword : derived) {
            assertTrue(keyword.getWeight() > 0 && keyword.getWeight() < KeywordList.USER_WEIGHT);
        }
    }

    @Test
    void skipsPagesThatCannotBeFetched() {
        WordCounter broken = new WordCounter("https://broken.example") {
            @Override
            protected String fetchContent() throws IOException {
                throw new IOException("HTTP 500");
            }
        };

        assertTrue(expander.deriveKeywords(
                List.of(new WebPage("https://broken.example", "t", broken)), "cat").isEmpty());
    }
}
