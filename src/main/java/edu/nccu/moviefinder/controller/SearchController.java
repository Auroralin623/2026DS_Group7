package edu.nccu.moviefinder.controller;

import edu.nccu.moviefinder.model.SearchResult;
import edu.nccu.moviefinder.service.SearchService;
import edu.nccu.moviefinder.service.SearchUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 只負責轉交請求和整理要顯示的資料,不處理商業邏輯。
 */
@Controller
public class SearchController {
    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("active", "search");
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("active", "about");
        return "about";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model, HttpServletResponse response) {
        if (q.isBlank()) {
            return "redirect:/";
        }
        ArrayList<SearchResult> results = new ArrayList<>();
        try {
            results = searchService.search(q);
        } catch (SearchUnavailableException e) {
            model.addAttribute("searchError", true);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        boolean narrow = ResultPresenter.isNarrow(q, results);
        model.addAttribute("q", q);
        model.addAttribute("active", "search");
        model.addAttribute("results", results);
        model.addAttribute("featured", narrow ? results.get(0) : null);
        model.addAttribute("featuredMatches", ResultPresenter.titleMatches(q, results));
        model.addAttribute("topN", Math.min(ResultPresenter.TOP_N, results.size()));
        model.addAttribute("sources", narrow ? ResultPresenter.relatedSources(results) : List.of());
        model.addAttribute("contributions",
                ResultPresenter.contributions(results.isEmpty() ? null : results.get(0)));
        model.addAttribute("details", ResultPresenter.details(results));
        model.addAttribute("visible", ResultPresenter.VISIBLE_RESULTS);
        model.addAttribute("relatedKeywords", results.isEmpty() ? List.of() : relatedKeywords(q));
        return "result";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam(defaultValue = "") String q, Model model, HttpServletResponse response) {
        model.addAttribute("active", "compare");
        if (q.isBlank()) {
            return "compare";
        }
        LinkedHashMap<String, String> tavily = new LinkedHashMap<>();
        try {
            tavily = searchService.googleResults(q);
        } catch (IOException | IllegalStateException e) {
            log.warn("Tavily search failed: {}", e.getMessage());
            model.addAttribute("googleError", true);
        }
        ArrayList<SearchResult> results = new ArrayList<>();
        try {
            results = searchService.search(q);
        } catch (SearchUnavailableException e) {
            model.addAttribute("resultsError", true);
        }
        if (model.containsAttribute("googleError") && model.containsAttribute("resultsError")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        model.addAttribute("q", q);
        model.addAttribute("google", tavily);
        model.addAttribute("tavilyRanks", ResultPresenter.ranks(tavily));
        model.addAttribute("results", results);
        model.addAttribute("visible", ResultPresenter.VISIBLE_RESULTS);
        return "compare";
    }

    private List<String> relatedKeywords(String q) {
        List<String> keywords = searchService.relatedKeywords(q);
        return keywords == null ? List.of() : keywords;
    }
}
