package ahocorasick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Dependency-free test suite (no JUnit needed, so it runs with plain javac/java).
 * Exits with status 1 if any test fails.
 */
public final class AhoCorasickTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        classicExample();
        overlappingMatches();
        patternsThatAreSuffixesOfEachOther();
        noMatchesAndForeignCharacters();
        emptyText();
        duplicatePatterns();
        patternLongerThanText();
        patternEqualsText();
        unicodeText();
        invalidPatternsAreRejected();
        failureLinksFallBackCorrectly();
        kmpPrefixFunction();
        randomizedCrossCheck();

        System.out.printf("%nTests passed: %d, failed: %d%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Textbook example from the original Aho–Corasick paper. */
    private static void classicExample() {
        String[] p = {"he", "she", "his", "hers"};
        expectMatches("classic he/she/his/hers in 'ushers'", p, "ushers",
                List.of(new Match(1, 1), new Match(2, 0), new Match(2, 3)));
    }

    /** Overlapping occurrences: "a" x3, "aa" x2, "aaa" x1. */
    private static void overlappingMatches() {
        String[] p = {"a", "aa", "aaa"};
        AhoCorasick ac = new AhoCorasick(p);
        check("overlapping matches count = 6", ac.countMatches("aaa") == 6);
        expectMatches("overlapping matches positions", p, "aaa", List.of(
                new Match(0, 0), new Match(0, 1), new Match(0, 2),
                new Match(1, 0), new Match(1, 1),
                new Match(2, 0)));
    }

    /** All four patterns end at the same position — requires output links to report all of them. */
    private static void patternsThatAreSuffixesOfEachOther() {
        String[] p = {"abcd", "bcd", "cd", "d"};
        expectMatches("suffix chain reported via output links", p, "abcd",
                List.of(new Match(0, 0), new Match(1, 1), new Match(2, 2), new Match(3, 3)));
    }

    private static void noMatchesAndForeignCharacters() {
        AhoCorasick ac = new AhoCorasick("abc", "bcd");
        check("no matches in text with foreign chars", ac.countMatches("xyz xyz 123") == 0);
        // foreign character in the middle must reset the state: "ab#c" must NOT match "abc"
        check("foreign char breaks a match", ac.countMatches("ab#cd") == 0);
        check("match right after a foreign char", ac.countMatches("#abc#") == 1);
    }

    private static void emptyText() {
        check("empty text has no matches", new AhoCorasick("a", "b").countMatches("") == 0);
    }

    private static void duplicatePatterns() {
        String[] p = {"ab", "ab"};
        expectMatches("duplicate patterns are both reported", p, "abab", List.of(
                new Match(0, 0), new Match(0, 1), new Match(2, 0), new Match(2, 1)));
    }

    private static void patternLongerThanText() {
        check("pattern longer than text", new AhoCorasick("abcdef").countMatches("abc") == 0);
    }

    private static void patternEqualsText() {
        expectMatches("pattern equals text", new String[]{"hello"}, "hello", List.of(new Match(0, 0)));
    }

    private static void unicodeText() {
        String[] p = {"привет", "мир", "ир"};
        expectMatches("cyrillic patterns", p, "привет, мир!", List.of(
                new Match(0, 0), new Match(8, 1), new Match(9, 2)));
    }

    private static void invalidPatternsAreRejected() {
        check("empty pattern array rejected", throwsIAE(() -> new AhoCorasick()));
        check("empty pattern rejected", throwsIAE(() -> new AhoCorasick("a", "")));
        check("null pattern rejected", throwsIAE(() -> new AhoCorasick("a", null)));
    }

    /**
     * "abcx" fails after "abc" to "bc" (prefix of "bcy"), which then completes on 'y'.
     * A naive restart from the root would miss "bcy".
     */
    private static void failureLinksFallBackCorrectly() {
        String[] p = {"abcx", "bcy"};
        expectMatches("failure link reuses already read characters", p, "abcy",
                List.of(new Match(1, 1)));
    }

    private static void kmpPrefixFunction() {
        int[] expected = {0, 1, 0, 1, 2, 2, 3};
        int[] actual = KMP.prefixFunction("aabaaab");
        check("KMP prefix function of 'aabaaab'", java.util.Arrays.equals(expected, actual));
    }

    /**
     * Random small-alphabet inputs (many overlaps and shared prefixes) compared against
     * a naive O(n·m) search and against KMP.
     */
    private static void randomizedCrossCheck() {
        Random rnd = new Random(12345);
        int trials = 500;
        int mismatches = 0;
        for (int t = 0; t < trials; t++) {
            int k = 1 + rnd.nextInt(30);
            String[] patterns = new String[k];
            for (int i = 0; i < k; i++) {
                patterns[i] = randomString(rnd, 1 + rnd.nextInt(6), "abc");
            }
            String text = randomString(rnd, rnd.nextInt(300), "abcd");

            List<Match> expected = naive(text, patterns);
            List<Match> ac = new AhoCorasick(patterns).searchAll(text);
            List<Match> kmp = KMP.findAll(text, patterns);
            if (!expected.equals(ac) || !expected.equals(kmp)) {
                mismatches++;
            }
        }
        check("randomized cross-check vs naive and KMP (" + trials + " trials)", mismatches == 0);
    }

    // ---------------------------------------------------------------------------------------------

    private static List<Match> naive(String text, String[] patterns) {
        List<Match> result = new ArrayList<>();
        for (int id = 0; id < patterns.length; id++) {
            for (int i = 0; i + patterns[id].length() <= text.length(); i++) {
                if (text.startsWith(patterns[id], i)) {
                    result.add(new Match(i, id));
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    private static String randomString(Random rnd, int length, String alphabet) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void expectMatches(String name, String[] patterns, String text, List<Match> expected) {
        List<Match> sortedExpected = new ArrayList<>(expected);
        Collections.sort(sortedExpected);
        List<Match> actual = new AhoCorasick(patterns).searchAll(text);
        boolean ok = actual.equals(sortedExpected);
        if (!ok) {
            System.out.println("    expected: " + sortedExpected);
            System.out.println("    actual:   " + actual);
        }
        check(name, ok);
    }

    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
