package edu.nccu.moviefinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubLinkCrawlerTest {
    private static final String BASE = "https://films.example.com/";

    private final SubLinkCrawler crawler = new SubLinkCrawler(5);

    @Test
    void resolvesRelativeLinks() {
        String html = "<a href='/reviews'>r</a><a href='cast.html'>c</a>";

        assertEquals(List.of("https://films.example.com/reviews", "https://films.example.com/cast.html"),
                crawler.extractLinks(html, BASE));
    }

    @Test
    void removesDuplicatesFragmentsAndTheBasePage() {
        String html = "<a href='/a'>1</a><a href='/a#top'>2</a><a href='/a'>3</a>"
                + "<a href='/'>home</a><a href='#top'>top</a>";

        assertEquals(List.of("https://films.example.com/a"), crawler.extractLinks(html, BASE));
    }

    @Test
    void keepsOnlySameHostHttpLinks() {
        String html = "<a href='https://other.example.org/x'>x</a><a href='mailto:a@example.com'>m</a>"
                + "<a href='javascript:void(0)'>j</a><a href='/ok'>ok</a>";

        assertEquals(List.of("https://films.example.com/ok"), crawler.extractLinks(html, BASE));
    }

    @Test
    void capsTheNumberOfLinks() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            html.append("<a href='/p").append(i).append("'>p</a>");
        }

        assertEquals(5, crawler.extractLinks(html.toString(), BASE).size());
    }
}
