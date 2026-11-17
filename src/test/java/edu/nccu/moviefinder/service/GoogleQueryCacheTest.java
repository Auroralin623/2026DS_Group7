
package edu.nccu.moviefinder.service;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class GoogleQueryCacheTest {

    private static class FakeGoogleQuery extends GoogleQuery {

        int apiCalls = 0;
        boolean shouldFail = false;

        FakeGoogleQuery() {
            super("fake-key", 10);
        }

        @Override
        protected LinkedHashMap<String, String> fetchFromTavily(
                String q) throws IOException {

            apiCalls++;

            if (shouldFail) {
                throw new IOException("API failed");
            }

            LinkedHashMap<String, String> results =
                    new LinkedHashMap<>();

            results.put("https://example.com", q);

            return results;
        }
    }

    @Test
    void sameQueryOnlyCallsApiOnce() throws IOException {

        FakeGoogleQuery google = new FakeGoogleQuery();

        var first = google.query("Inception");
        var second = google.query("Inception");

        assertEquals(first, second);
        assertEquals(1, google.apiCalls);
    }

    @Test
    void differentQueriesCallApiSeparately() throws IOException {

        FakeGoogleQuery google = new FakeGoogleQuery();

        google.query("Inception");
        google.query("Titanic");

        assertEquals(2, google.apiCalls);
    }

    @Test
    void failedRequestsAreNotCached() throws IOException {

        FakeGoogleQuery google = new FakeGoogleQuery();

        google.shouldFail = true;

        assertThrows(IOException.class,
                () -> google.query("Inception"));

        google.shouldFail = false;

        google.query("Inception");
        google.query("Inception");

        assertEquals(2, google.apiCalls);
    }

    @Test
    void modifyingResultDoesNotAffectCache() throws IOException {

        FakeGoogleQuery google = new FakeGoogleQuery();

        var first = google.query("Inception");
        first.clear();

        var second = google.query("Inception");

        assertEquals(1, second.size());
        assertEquals(1, google.apiCalls);
    }

    @Test
    void cacheEvictsLeastRecentlyUsedQuery() throws IOException {

        FakeGoogleQuery google = new FakeGoogleQuery();

        for (int i = 0; i < 100; i++) {
            google.query("movie" + i);
        }

        google.query("movie0");
        google.query("movie100");

        assertEquals(101, google.apiCalls);

        google.query("movie0");

        assertEquals(101, google.apiCalls);

        google.query("movie1");

        assertEquals(102, google.apiCalls);
    }
}
