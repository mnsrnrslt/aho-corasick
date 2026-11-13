package ahocorasick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Knuth–Morris–Pratt single-pattern search, used as the baseline:
 * to find k patterns, KMP is simply run k times over the same text.
 */
public final class KMP {

    private KMP() {
    }

    /**
     * pi[i] = length of the longest proper prefix of p[0..i] that is also a suffix of p[0..i].
     * Runs in O(m). This is the single-pattern analogue of Aho–Corasick failure links.
     */
    public static int[] prefixFunction(String p) {
        int[] pi = new int[p.length()];
        int k = 0;
        for (int i = 1; i < p.length(); i++) {
            while (k > 0 && p.charAt(i) != p.charAt(k)) {
                k = pi[k - 1];
            }
            if (p.charAt(i) == p.charAt(k)) {
                k++;
            }
            pi[i] = k;
        }
        return pi;
    }

    /** Finds all occurrences of one pattern in O(n + m). */
    public static void search(String text, String pattern, int patternIndex, MatchListener listener) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern must be non-null and non-empty");
        }
        int[] pi = prefixFunction(pattern);
        int m = pattern.length();
        int k = 0;                                   // length of the currently matched prefix
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (k > 0 && c != pattern.charAt(k)) {
                k = pi[k - 1];
            }
            if (c == pattern.charAt(k)) {
                k++;
            }
            if (k == m) {
                listener.onMatch(i - m + 1, patternIndex);
                k = pi[k - 1];                       // continue to allow overlapping matches
            }
        }
    }

    /** Runs KMP separately for every pattern: O(k·n + L) in total. */
    public static void searchAll(String text, String[] patterns, MatchListener listener) {
        for (int id = 0; id < patterns.length; id++) {
            search(text, patterns[id], id, listener);
        }
    }

    public static List<Match> findAll(String text, String[] patterns) {
        List<Match> result = new ArrayList<>();
        searchAll(text, patterns, (start, id) -> result.add(new Match(start, id)));
        Collections.sort(result);
        return result;
    }

    public static long countAll(String text, String[] patterns) {
        long[] counter = {0};
        searchAll(text, patterns, (start, id) -> counter[0]++);
        return counter[0];
    }
}
