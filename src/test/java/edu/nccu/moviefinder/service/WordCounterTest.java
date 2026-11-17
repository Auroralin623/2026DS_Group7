package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class WordCounterTest {

    private static int count(String content, String keyword) throws IOException {
        return new WordCounter("https://example.com", content).countKeyword(keyword);
    }

    @Test
    void countsCaseInsensitively() throws IOException {
        assertEquals(3, count("movie Movie MOVIE", "movie"));
    }

    @Test
    void countsChineseKeywords() throws IOException {
        assertEquals(2, count("這部電影是好電影", "電影"));
    }

    @Test
    void returnsZeroWhenAbsentOrEmpty() throws IOException {
        assertEquals(0, count("a film", "movie"));
        assertEquals(0, count("a film", ""));
        assertEquals(0, count("", "movie"));
    }

    @Test
    void doesNotCountEnglishKeywordsInsideLongerWords() throws IOException {
        assertEquals(1, count("category education cat", "cat"));
        assertEquals(1, count("email said AI main", "AI"));
    }

    @Test
    void wholeWordMatchingAcceptsPunctuationAndKeepsChineseWorking() throws IOException {
        assertEquals(2, count("A cat, a (cat).", "cat"));
        assertEquals(2, count("這部電影是好電影", "電影"));
    }
}
