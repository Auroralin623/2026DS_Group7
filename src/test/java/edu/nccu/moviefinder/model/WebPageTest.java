package edu.nccu.moviefinder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.nccu.moviefinder.service.WordCounter;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class WebPageTest {

    @Test
    void scoresKeywordsAndRecordsTheirCounts() throws IOException {
        WebPage page = new WebPage("https://example.com", "Example",
                new WordCounter("https://example.com", "movie movie cat"));
        KeywordList keywords = new KeywordList();
        keywords.add(new Keyword("movie", 2));
        keywords.add(new Keyword("cat", -1));

        page.setScore(keywords);

        assertEquals(3.0, page.getScore());
        assertEquals(2, page.getCounts().get("movie"));
        assertEquals(1, page.getCounts().get("cat"));
    }

    @Test
    void scoringAgainDoesNotAccumulatePreviousResults() throws IOException {
        WebPage page = new WebPage("https://example.com", "Example",
                new WordCounter("https://example.com", "movie movie"));
        KeywordList keywords = new KeywordList();
        keywords.add(new Keyword("movie", 2));

        page.setScore(keywords);
        page.setScore(keywords);

        assertEquals(4.0, page.getScore());
        assertEquals(2, page.getCounts().get("movie"));
    }

    @Test
    void nullKeywordsResetScoreAndCounts() throws IOException {
        WebPage page = new WebPage("https://example.com", "Example",
                new WordCounter("https://example.com", "movie"));
        KeywordList keywords = new KeywordList();
        keywords.add(new Keyword("movie", 2));
        page.setScore(keywords);

        page.setScore(null);

        assertEquals(0.0, page.getScore());
        assertTrue(page.getCounts().isEmpty());
    }
}
