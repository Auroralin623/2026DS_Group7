package edu.nccu.moviefinder.service;

import edu.nccu.moviefinder.model.Keyword;
import edu.nccu.moviefinder.model.KeywordList;
import edu.nccu.moviefinder.model.WebPage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Expands a user's movie-related query and discovers useful terms from retrieved pages.
 *
 * Stage 1 uses a lightweight TF-IDF approach. It does not require an external NLP service
 * or an additional Maven dependency. The current tokenizer targets English words; a
 * Chinese word segmenter can be added later if Chinese keyword extraction is required.
 */
@Service
public class KeywordExpander {

    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final int MAX_DERIVED_KEYWORDS = 5;
    private static final double MAX_DERIVED_WEIGHT = KeywordList.USER_WEIGHT * 0.8;

    private final Set<String> stopwords;

    public KeywordExpander() {
        this.stopwords = loadStopwords();
    }

    /**
     * Adds movie-domain context to a query.
     * Example: "cat" -> ["cat movie", "cat film", "cat movie review"].
     */
    public List<String> expand(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        String query = q.trim().replaceAll("\\s+", " ");
        return List.of(
                query + " movie",
                query + " film",
                query + " movie review");
    }

    /**
     * Extracts up to five useful English terms from the supplied pages using TF-IDF.
     *
     * Rules:
     * - stopwords and words from the original query are excluded;
     * - pages whose content cannot be fetched are skipped;
     * - returned weights are positive and strictly below KeywordList.USER_WEIGHT;
     * - results are ordered by their aggregated TF-IDF score (descending).
     */
    public List<Keyword> deriveKeywords(List<WebPage> topPages, String originalQuery) {
        if (topPages == null || topPages.isEmpty()) {
            return List.of();
        }

        Set<String> excludedTerms = new HashSet<>(stopwords);
        excludedTerms.addAll(tokenizeToSet(originalQuery));

        List<Map<String, Integer>> documents = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (WebPage page : topPages) {
            if (page == null) {
                continue;
            }

            final String content;
            try {
                content = page.getContent();
            } catch (IOException | RuntimeException e) {
                // A failed page should not prevent keyword extraction from other pages.
                continue;
            }

            if (content == null || content.isBlank()) {
                continue;
            }

            Map<String, Integer> termFrequency = countTerms(content, excludedTerms);
            if (termFrequency.isEmpty()) {
                continue;
            }

            documents.add(termFrequency);
            for (String term : termFrequency.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        if (documents.isEmpty()) {
            return List.of();
        }

        int documentCount = documents.size();
        Map<String, Double> totalScores = new HashMap<>();

        // TF-IDF: (1 + ln(term frequency)) * (ln((N + 1) / (df + 1)) + 1).
        // Sum each term's score across the retrieved pages.
        for (Map<String, Integer> document : documents) {
            for (Map.Entry<String, Integer> entry : document.entrySet()) {
                String term = entry.getKey();
                int frequency = entry.getValue();
                int docFrequency = documentFrequency.getOrDefault(term, 0);

                double tf = 1.0 + Math.log(frequency);
                double idf = Math.log((documentCount + 1.0) / (docFrequency + 1.0)) + 1.0;
                double tfIdf = tf * idf;

                totalScores.merge(term, tfIdf, Double::sum);
            }
        }

        if (totalScores.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<String, Double>> rankedTerms = new ArrayList<>(totalScores.entrySet());
        rankedTerms.sort(
                Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey));

        double maxScore = rankedTerms.get(0).getValue();
        int resultCount = Math.min(MAX_DERIVED_KEYWORDS, rankedTerms.size());
        List<Keyword> result = new ArrayList<>(resultCount);

        for (int i = 0; i < resultCount; i++) {
            Map.Entry<String, Double> entry = rankedTerms.get(i);
            double weight = MAX_DERIVED_WEIGHT * entry.getValue() / maxScore;

            // Guard against floating-point edge cases while preserving 0 < weight < USER_WEIGHT.
            weight = Math.max(0.000001, Math.min(MAX_DERIVED_WEIGHT, weight));
            result.add(new Keyword(entry.getKey(), weight));
        }

        return result;
    }

    private Map<String, Integer> countTerms(String text, Set<String> excludedTerms) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = ENGLISH_WORD.matcher(text.toLowerCase(Locale.ROOT));

        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() < 2 || excludedTerms.contains(term)) {
                continue;
            }
            counts.merge(term, 1, Integer::sum);
        }
        return counts;
    }

    private Set<String> tokenizeToSet(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        Matcher matcher = ENGLISH_WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private Set<String> loadStopwords() {
        Set<String> words = new LinkedHashSet<>();

        try (InputStream input = KeywordExpander.class.getResourceAsStream("/stopwords.txt")) {
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String word = line.trim().toLowerCase(Locale.ROOT);
                        if (!word.isEmpty() && !word.startsWith("#")) {
                            words.add(word);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Keep the built-in fallback terms below if the resource cannot be read.
        }

        // Fallback/common terms also protect tests or unusual launches without resources.
        words.addAll(Set.of(
                "a", "an", "and", "are", "as", "at", "be", "been", "being", "but",
                "by", "can", "could", "did", "do", "does", "doing", "down", "during",
                "for", "from", "had", "has", "have", "he", "her", "here", "hers", "him",
                "his", "how", "i", "if", "in", "into", "is", "it", "its", "just", "me",
                "more", "most", "my", "no", "not", "of", "off", "on", "once", "only",
                "or", "other", "our", "out", "over", "she", "should", "so", "some", "such",
                "than", "that", "the", "their", "them", "then", "there", "these", "they",
                "this", "those", "through", "to", "too", "under", "until", "up", "very",
                "was", "we", "were", "what", "when", "where", "which", "while", "who",
                "why", "will", "with", "would", "you", "your", "about", "after", "also",
                "before", "between", "movie", "movies", "film", "films", "review",
                "reviews", "website", "webpage", "page", "http", "https", "www", "com"));

        return words;
    }
}
