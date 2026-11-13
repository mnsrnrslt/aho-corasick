package ahocorasick;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Experimental comparison of Aho–Corasick vs. running KMP once per pattern.
 *
 * <p>Grid: {10, 100, 1000} patterns × {1 000, 10 000, 100 000} text characters,
 * for two alphabets (26 lowercase letters and the 4-letter DNA alphabet).
 * Each configuration is executed {@value #RUNS} times after {@value #WARMUP_RUNS} warm-up runs;
 * the reported value is the average of the measured runs (System.nanoTime()).
 *
 * <p>Pattern generation: lengths are uniform in [{@value #MIN_LEN}, {@value #MAX_LEN}];
 * half of the patterns are substrings cut from the text (guaranteed to occur),
 * the other half are random strings over the same alphabet.
 */
public final class Benchmark {

    private static final int[] PATTERN_COUNTS = {10, 100, 1_000};
    private static final int[] TEXT_LENGTHS = {1_000, 10_000, 100_000};
    private static final int RUNS = 5;
    private static final int WARMUP_RUNS = 3;
    private static final int MIN_LEN = 4;
    private static final int MAX_LEN = 12;
    private static final long SEED = 42L;

    private static final String[][] ALPHABETS = {
            {"lowercase a-z (sigma=26)", "abcdefghijklmnopqrstuvwxyz"},
            {"DNA ACGT (sigma=4)", "ACGT"},
    };

    /** Prevents the JIT from removing "unused" work. */
    private static long sink = 0;

    private record Config(String alphabetName, String text, String[] patterns) {
    }

    private record Result(String alphabet, int patterns, int textLength, int trieNodes, long matches,
                          double acBuildMs, double acSearchMs, double acTotalMs, double kmpTotalMs) {
        double speedup() {
            return kmpTotalMs / acTotalMs;
        }
    }

    public static void main(String[] args) throws IOException {
        Random rnd = new Random(SEED);
        List<Config> configs = new ArrayList<>();
        for (String[] alphabet : ALPHABETS) {
            for (int n : TEXT_LENGTHS) {
                String text = randomString(rnd, n, alphabet[1]);
                for (int k : PATTERN_COUNTS) {
                    configs.add(new Config(alphabet[0], text, generatePatterns(rnd, text, k, alphabet[1])));
                }
            }
        }

        System.out.println("Warming up the JIT...");
        for (int w = 0; w < WARMUP_RUNS; w++) {
            for (Config c : configs) {
                sink += new AhoCorasick(c.patterns).countMatches(c.text);
                sink += KMP.countAll(c.text, c.patterns);
            }
        }

        System.out.println("Measuring (" + RUNS + " runs per configuration)...\n");
        List<Result> results = new ArrayList<>();
        for (Config c : configs) {
            results.add(measure(c));
        }

        String markdown = toMarkdown(results);
        System.out.println(markdown);
        System.out.println("(sink = " + sink + ")");

        Path dir = Path.of("results");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("results.csv"), toCsv(results));
        Files.writeString(dir.resolve("results.md"), markdown);
        System.out.println("Saved results/results.csv and results/results.md");
    }

    private static Result measure(Config c) {
        long buildSum = 0, searchSum = 0, kmpSum = 0;
        long acMatches = -1, kmpMatches = -1;
        int nodes = 0;

        for (int r = 0; r < RUNS; r++) {
            long t0 = System.nanoTime();
            AhoCorasick ac = new AhoCorasick(c.patterns);
            long t1 = System.nanoTime();
            acMatches = ac.countMatches(c.text);
            long t2 = System.nanoTime();
            buildSum += t1 - t0;
            searchSum += t2 - t1;
            nodes = ac.nodeCount();

            long t3 = System.nanoTime();
            kmpMatches = KMP.countAll(c.text, c.patterns);
            long t4 = System.nanoTime();
            kmpSum += t4 - t3;

            sink += acMatches + kmpMatches;
        }

        if (acMatches != kmpMatches) {
            throw new IllegalStateException("Match counts differ: AC=" + acMatches + " KMP=" + kmpMatches);
        }

        double build = buildSum / (RUNS * 1e6);
        double search = searchSum / (RUNS * 1e6);
        return new Result(c.alphabetName, c.patterns.length, c.text.length(), nodes, acMatches,
                build, search, build + search, kmpSum / (RUNS * 1e6));
    }

    private static String[] generatePatterns(Random rnd, String text, int count, String alphabet) {
        String[] patterns = new String[count];
        for (int i = 0; i < count; i++) {
            int len = MIN_LEN + rnd.nextInt(MAX_LEN - MIN_LEN + 1);
            if (i % 2 == 0) {
                int start = rnd.nextInt(text.length() - len + 1);
                patterns[i] = text.substring(start, start + len);   // guaranteed to occur
            } else {
                patterns[i] = randomString(rnd, len, alphabet);     // may or may not occur
            }
        }
        return patterns;
    }

    private static String randomString(Random rnd, int length, String alphabet) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String toMarkdown(List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Environment: Java %s, %s %s, %d logical CPUs. Times are averages of %d runs, in ms.%n",
                System.getProperty("java.version"), System.getProperty("os.name"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), RUNS));
        String current = null;
        for (Result r : results) {
            if (!r.alphabet.equals(current)) {
                current = r.alphabet;
                sb.append("\n### Alphabet: ").append(current).append("\n\n");
                sb.append("| Patterns | Text length | Trie nodes | Matches | AC build | AC search | AC total | KMP x k | KMP / AC |\n");
                sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            }
            sb.append(String.format(Locale.ROOT, "| %d | %,d | %,d | %,d | %.3f | %.3f | %.3f | %.3f | %.1fx |%n",
                    r.patterns, r.textLength, r.trieNodes, r.matches,
                    r.acBuildMs, r.acSearchMs, r.acTotalMs, r.kmpTotalMs, r.speedup()));
        }
        return sb.toString();
    }

    private static String toCsv(List<Result> results) {
        StringBuilder sb = new StringBuilder(
                "alphabet,patterns,text_length,trie_nodes,matches,ac_build_ms,ac_search_ms,ac_total_ms,kmp_total_ms,kmp_over_ac\n");
        for (Result r : results) {
            sb.append(String.format(Locale.ROOT, "\"%s\",%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f%n",
                    r.alphabet, r.patterns, r.textLength, r.trieNodes, r.matches,
                    r.acBuildMs, r.acSearchMs, r.acTotalMs, r.kmpTotalMs, r.speedup()));
        }
        return sb.toString();
    }
}
