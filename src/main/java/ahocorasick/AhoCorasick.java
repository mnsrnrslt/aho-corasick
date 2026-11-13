package ahocorasick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Aho–Corasick automaton for finding all occurrences of many patterns in one pass over the text.
 *
 * <p>The automaton consists of three parts:
 * <ul>
 *   <li><b>Trie (goto function)</b> — {@code children[node][symbol]}. Every node represents a prefix
 *       of at least one pattern; the root represents the empty string.</li>
 *   <li><b>Failure links</b> — {@code fail[node]} points to the node of the <i>longest proper suffix</i>
 *       of the node's string that is also present in the trie. When the next text character has no
 *       trie edge, we follow failure links instead of restarting from the root.</li>
 *   <li><b>Output links</b> — {@code outLink[node]} points to the nearest node on the failure chain
 *       where some pattern ends. They let us report every pattern that ends at the current text
 *       position without walking failure links that lead to non-terminal nodes.</li>
 * </ul>
 *
 * <p>Characters are mapped to a compact alphabet (only characters that occur in the patterns),
 * so each trie node stores an {@code int[σ]} array, where σ is the number of distinct pattern characters.
 * A text character that occurs in no pattern cannot continue any match, so the search resets to the root.
 */
public final class AhoCorasick {

    private static final int ROOT = 0;
    private static final int NONE = -1;

    private final String[] patterns;
    /** char -> compact symbol index, or NONE if the character does not occur in any pattern. */
    private final int[] symbolOf;
    private final int alphabetSize;

    private final int[][] children;   // trie edges: children[node][symbol] -> child node or NONE
    private final int[] fail;         // failure links
    private final int[] outLink;      // output (dictionary suffix) links, NONE if there is none
    private final int[][] endsHere;   // ids of patterns that end exactly at this node
    private final int nodeCount;

    /**
     * Builds the automaton. Total build time is O(L · σ), where L is the total length of all patterns.
     *
     * @throws IllegalArgumentException if patterns are null/empty or any pattern is null/empty
     */
    public AhoCorasick(String... patterns) {
        validate(patterns);
        this.patterns = patterns.clone();

        // ---- Step 0: alphabet compression -------------------------------------------------------
        // The lookup table only spans [0, max pattern char], e.g. 123 entries for 'a'..'z',
        // instead of all 65 536 possible chars.
        int maxChar = 0;
        for (String p : this.patterns) {
            for (int i = 0; i < p.length(); i++) {
                maxChar = Math.max(maxChar, p.charAt(i));
            }
        }
        this.symbolOf = new int[maxChar + 1];
        Arrays.fill(symbolOf, NONE);
        int sigma = 0;
        for (String p : this.patterns) {
            for (int i = 0; i < p.length(); i++) {
                char c = p.charAt(i);
                if (symbolOf[c] == NONE) {
                    symbolOf[c] = sigma++;
                }
            }
        }
        this.alphabetSize = sigma;

        // ---- Step 1: trie construction ------------------------------------------------------------
        List<int[]> childList = new ArrayList<>();
        List<List<Integer>> endList = new ArrayList<>();
        childList.add(newRow(sigma));            // node 0 = root
        endList.add(new ArrayList<>());

        for (int id = 0; id < this.patterns.length; id++) {
            String p = this.patterns[id];
            int node = ROOT;
            for (int i = 0; i < p.length(); i++) {
                int s = symbolOf[p.charAt(i)];
                if (childList.get(node)[s] == NONE) {
                    childList.get(node)[s] = childList.size();
                    childList.add(newRow(sigma));
                    endList.add(new ArrayList<>());
                }
                node = childList.get(node)[s];
            }
            endList.get(node).add(id);           // pattern `id` ends at this node
        }

        this.nodeCount = childList.size();
        this.children = childList.toArray(new int[0][]);
        this.endsHere = new int[nodeCount][];
        for (int v = 0; v < nodeCount; v++) {
            this.endsHere[v] = endList.get(v).stream().mapToInt(Integer::intValue).toArray();
        }

        // ---- Steps 2 + 3: failure links and output links (BFS) -----------------------------------
        this.fail = new int[nodeCount];
        this.outLink = new int[nodeCount];
        buildFailureAndOutputLinks();
    }

    /**
     * BFS over the trie. Nodes are processed in order of depth, so when we compute the failure link
     * of a node at depth d, all failure links of nodes at depth &lt; d are already known.
     *
     * <p>For an edge u --c--> v: start from f = fail[u] and keep following failure links until we find
     * a node that has an edge labelled c (or reach the root). That edge's target is fail[v].
     * The output link of v is fail[v] if a pattern ends there, otherwise it is inherited: outLink[fail[v]].
     */
    private void buildFailureAndOutputLinks() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        fail[ROOT] = ROOT;
        outLink[ROOT] = NONE;

        // Depth-1 nodes: the longest proper suffix of a single character is the empty string -> root.
        for (int s = 0; s < alphabetSize; s++) {
            int v = children[ROOT][s];
            if (v != NONE) {
                fail[v] = ROOT;
                outLink[v] = NONE;
                queue.add(v);
            }
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int s = 0; s < alphabetSize; s++) {
                int v = children[u][s];
                if (v == NONE) {
                    continue;
                }
                int f = fail[u];
                while (f != ROOT && children[f][s] == NONE) {
                    f = fail[f];
                }
                int target = children[f][s];
                fail[v] = (target == NONE) ? ROOT : target;

                int fv = fail[v];
                outLink[v] = (endsHere[fv].length > 0) ? fv : outLink[fv];
                queue.add(v);
            }
        }
    }

    /**
     * Scans the text once and reports every occurrence of every pattern to the listener.
     * Runs in O(n + z), where n is the text length and z is the number of reported matches.
     */
    public void search(String text, MatchListener listener) {
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int s = (c < symbolOf.length) ? symbolOf[c] : NONE;
            if (s == NONE) {                     // character occurs in no pattern -> no match can continue
                state = ROOT;
                continue;
            }
            // Follow failure links until there is an edge for s (or we are at the root).
            while (state != ROOT && children[state][s] == NONE) {
                state = fail[state];
            }
            int next = children[state][s];
            state = (next == NONE) ? ROOT : next;

            // Report patterns ending at position i: the current node and everything on its output chain.
            int t = (endsHere[state].length > 0) ? state : outLink[state];
            while (t != NONE) {
                for (int id : endsHere[t]) {
                    listener.onMatch(i - patterns[id].length() + 1, id);
                }
                t = outLink[t];
            }
        }
    }

    /** Returns all matches sorted by start position, then by pattern index. */
    public List<Match> searchAll(String text) {
        List<Match> result = new ArrayList<>();
        search(text, (start, id) -> result.add(new Match(start, id)));
        Collections.sort(result);
        return result;
    }

    /** Counts matches without allocating a result list (used in benchmarks). */
    public long countMatches(String text) {
        long[] counter = {0};
        search(text, (start, id) -> counter[0]++);
        return counter[0];
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int alphabetSize() {
        return alphabetSize;
    }

    public int patternCount() {
        return patterns.length;
    }

    public String pattern(int id) {
        return patterns[id];
    }

    private static int[] newRow(int sigma) {
        int[] row = new int[sigma];
        Arrays.fill(row, NONE);
        return row;
    }

    private static void validate(String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            throw new IllegalArgumentException("At least one pattern is required");
        }
        for (String p : patterns) {
            if (p == null || p.isEmpty()) {
                throw new IllegalArgumentException("Patterns must be non-null and non-empty");
            }
        }
    }
}
