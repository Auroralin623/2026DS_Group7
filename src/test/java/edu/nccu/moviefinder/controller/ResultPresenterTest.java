package edu.nccu.moviefinder.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.nccu.moviefinder.model.SearchResult;
import edu.nccu.moviefinder.model.WebNode;
import edu.nccu.moviefinder.model.WebPage;
import edu.nccu.moviefinder.model.WebTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultPresenterTest {

    private static SearchResult result(String title, String url) {
        WebTree tree = new WebTree(WebNode.fixture(WebPage.fixture(url, title, new HashMap<>(), 0), 0));
        return new SearchResult(1, title, url, 0, new HashMap<>(), tree);
    }

    private static List<SearchResult> results(String... titleAndUrl) {
        List<SearchResult> list = new ArrayList<>();
        for (int i = 0; i < titleAndUrl.length; i += 2) {
            list.add(result(titleAndUrl[i], titleAndUrl[i + 1]));
        }
        return list;
    }

    @Test
    void recognisesMovieSitesIncludingSubdomains() {
        assertEquals("Wikipedia", ResultPresenter.sourceLabel("https://en.wikipedia.org/wiki/Dog_(2022_film)"));
        assertEquals("IMDb", ResultPresenter.sourceLabel("https://www.imdb.com/title/tt11252248"));
        assertEquals("Rotten Tomatoes", ResultPresenter.sourceLabel("https://www.rottentomatoes.com/m/dog_2022"));
        assertNull(ResultPresenter.sourceLabel("https://www.catsmovie.sg"));
        assertNull(ResultPresenter.sourceLabel("not a url"));
    }

    @Test
    void doesNotMistakeLookalikeDomains() {
        assertNull(ResultPresenter.sourceLabel("https://notimdb.com/x"));
    }

    @Test
    void titleSearchIsNarrow() {
        List<SearchResult> list = results(
                "Dog (2022 film) - Wikipedia", "https://en.wikipedia.org/wiki/Dog",
                "Dog (2022) | Rotten Tomatoes", "https://www.rottentomatoes.com/m/dog_2022",
                "Best Movie Dogs of All Time", "https://www.rollingstone.com/x");

        assertTrue(ResultPresenter.isNarrow("dog movie", list));
        assertEquals(2, ResultPresenter.titleMatches("dog movie", list));
    }

    @Test
    void themeSearchIsBroad() {
        List<SearchResult> list = results(
                "The 20 Best Animal Movies", "https://a.example",
                "Animal Movies: Our Top Picks", "https://b.example",
                "40 Great Animal Movies", "https://c.example");

        assertFalse(ResultPresenter.isNarrow("animals", list));
    }

    @Test
    void emptyResultsAreNotNarrow() {
        assertFalse(ResultPresenter.isNarrow("dog", List.of()));
        assertTrue(ResultPresenter.relatedSources(List.of()).isEmpty());
    }

    @Test
    void relatedSourcesSkipTheTopResultAndDuplicates() {
        List<SearchResult> list = results(
                "Top", "https://en.wikipedia.org/wiki/Top",
                "A", "https://www.imdb.com/a",
                "B", "https://www.imdb.com/b",
                "C", "https://unknown.example",
                "D", "https://www.reddit.com/r/movies");

        List<ResultPresenter.Source> sources = ResultPresenter.relatedSources(list);

        assertEquals(List.of("IMDb", "Reddit"), sources.stream().map(ResultPresenter.Source::label).toList());
        assertEquals("https://www.imdb.com/a", sources.get(0).url());
    }

    @Test
    void contributionsAreSortedWithPercentOfTheLargest() {
        HashMap<String, Double> breakdown = new HashMap<>();
        breakdown.put("cat", 40.0);
        breakdown.put("review", 20.0);
        breakdown.put("ticket", -10.0);
        WebTree tree = new WebTree(WebNode.fixture(WebPage.fixture("u", "t", new HashMap<>(), 50), 50));

        List<ResultPresenter.Contribution> list =
                ResultPresenter.contributions(new SearchResult(1, "t", "u", 50, breakdown, tree));

        assertEquals(List.of("cat", "review", "ticket"), list.stream().map(ResultPresenter.Contribution::keyword).toList());
        assertEquals(100, list.get(0).percent());
        assertEquals(50, list.get(1).percent());
        assertEquals(25, list.get(2).percent());
    }

    @Test
    void contributionsLeaveOutKeywordsThatWouldShowAsZero() {
        HashMap<String, Double> breakdown = new HashMap<>();
        breakdown.put("cat", 40.0);
        breakdown.put("電影", 0.0);
        breakdown.put("showtimes", -0.0);
        breakdown.put("plot", 0.04);
        breakdown.put("ticket", -0.5);
        WebTree tree = new WebTree(WebNode.fixture(WebPage.fixture("u", "t", new HashMap<>(), 39.5), 39.5));

        List<ResultPresenter.Contribution> list =
                ResultPresenter.contributions(new SearchResult(1, "t", "u", 39.5, breakdown, tree));

        assertEquals(List.of("cat", "ticket"), list.stream().map(ResultPresenter.Contribution::keyword).toList());
    }

    @Test
    void detailsAreSortedForEveryResult() {
        HashMap<String, Double> first = new HashMap<>();
        first.put("review", 20.0);
        first.put("cat", 40.0);
        HashMap<String, Double> second = new HashMap<>();
        second.put("film", 5.0);
        second.put("cat", 3.0);
        WebTree tree = new WebTree(WebNode.fixture(WebPage.fixture("u", "t", new HashMap<>(), 0), 0));

        Map<Integer, List<ResultPresenter.Contribution>> details = ResultPresenter.details(List.of(
                new SearchResult(1, "a", "https://a.example", 60, first, tree),
                new SearchResult(2, "b", "https://b.example", 8, second, tree)));

        assertEquals(List.of("cat", "review"), details.get(1).stream().map(ResultPresenter.Contribution::keyword).toList());
        assertEquals(List.of("film", "cat"), details.get(2).stream().map(ResultPresenter.Contribution::keyword).toList());
    }

    @Test
    void ranksFollowTavilyOrder() {
        LinkedHashMap<String, String> tavily = new LinkedHashMap<>();
        tavily.put("https://b.example", "B");
        tavily.put("https://a.example", "A");

        Map<String, Integer> ranks = ResultPresenter.ranks(tavily);

        assertEquals(1, ranks.get("https://b.example"));
        assertEquals(2, ranks.get("https://a.example"));
    }
}
