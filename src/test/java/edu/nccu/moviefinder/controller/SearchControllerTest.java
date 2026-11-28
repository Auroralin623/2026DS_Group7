package edu.nccu.moviefinder.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 在 mock profile 下確認每個頁面都能正常顯示。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void homeShowsTheSearchBarAndThemeCards() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("Explore by Theme")))
                .andExpect(content().string(containsString("/search?q=cooking")))
                .andExpect(content().string(containsString("/search?q=AI")));
    }

    @Test
    void broadSearchShowsTheListWithScoresAndSidebar() throws Exception {
        mvc.perform(get("/search").param("q", "animals"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The 20 Best Animals Movies of All Time")))
                .andExpect(content().string(containsString("6 websites found")))
                .andExpect(content().string(containsString("Score breakdown")))
                .andExpect(content().string(containsString("Related keywords")))
                .andExpect(content().string(containsString("Cast and crew")))
                .andExpect(content().string(not(containsString("Top match"))));
    }

    @Test
    void titleSearchShowsTheTopMatchWithRelatedSources() throws Exception {
        mvc.perform(get("/search").param("q", "friendship movie"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Top match")))
                .andExpect(content().string(containsString("Found in 4 of the top 5 results")))
                .andExpect(content().string(containsString(">Rotten Tomatoes<")))
                .andExpect(content().string(containsString(">IMDb<")));
    }

    @Test
    void pagesIncludeTheWaitingScreenHiddenUntilASearchStarts() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"loading\" role=\"status\" aria-live=\"polite\" hidden")))
                .andExpect(content().string(containsString("This can take up to a minute.")));
    }

    @Test
    void shortListsHaveNoShowAllButton() throws Exception {
        mvc.perform(get("/search").param("q", "animals"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"show-all\""))))
                .andExpect(content().string(not(containsString("is-collapsed"))));
    }

    @Test
    void searchNumbersEachResult() throws Exception {
        mvc.perform(get("/search").param("q", "cat"))
                .andExpect(content().string(containsString("class=\"result-rank\">01<")))
                .andExpect(content().string(containsString("class=\"result-rank\">06<")));
    }

    @Test
    void compareShowsBothRankingsAndRankChanges() throws Exception {
        mvc.perform(get("/compare").param("q", "cat"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Example News about cat")))
                .andExpect(content().string(containsString("move-up")))
                .andExpect(content().string(containsString("move-down")));
    }

    @Test
    void compareWithoutAQueryAsksForOne() throws Exception {
        mvc.perform(get("/compare"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a theme to see how MovieFinder re-ranks Tavily results.")));
    }

    @Test
    void aboutPageExplainsTheRanking() throws Exception {
        mvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("How search and ranking work")))
                .andExpect(content().string(containsString("Tavily")));
    }

    @Test
    void aboutPageInChineseShowsTheStepLabels() throws Exception {
        mvc.perform(get("/about").param("lang", "zh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("搜尋與排名方式")))
                .andExpect(content().string(containsString("<strong>主題搜尋</strong>")))
                .andExpect(content().string(containsString("國立政治大學｜115-1")));
    }

    @Test
    void blankQueryReturnsToTheHomePage() throws Exception {
        mvc.perform(get("/search").param("q", "  "))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void pagesAreEnglishByDefault() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString(">Search<")))
                .andExpect(content().string(containsString("<html lang=\"en\"")));
    }

    @Test
    void languageCanBeSwitchedToChinese() throws Exception {
        mvc.perform(get("/search").param("q", "cat").param("lang", "zh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">搜尋<")))
                .andExpect(content().string(containsString("網站結果")))
                .andExpect(content().string(containsString("<html lang=\"zh-Hant\"")));
    }
}
