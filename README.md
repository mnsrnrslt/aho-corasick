# Aho–Corasick Multi-Pattern String Matching (Java)

Implementation of the Aho–Corasick algorithm in Java, with a KMP baseline,
correctness tests and an experimental performance comparison.

## Project structure

```
src/main/java/ahocorasick/
  AhoCorasick.java     trie, failure links, output links, search
  KMP.java             prefix function + KMP search (baseline: run once per pattern)
  Match.java           (start position, pattern index) record
  MatchListener.java   callback interface for reported matches
  Benchmark.java       experiments: 10/100/1000 patterns x 1k/10k/100k chars, 5 runs each
src/test/java/ahocorasick/
  AhoCorasickTest.java dependency-free tests (edge cases + randomized cross-check)
results/
  results.csv          raw averaged measurements
  results.md           the same results as markdown tables
run.sh                 compile + run tests and/or benchmark
REPORT.md              one-page explanation of the algorithm + complexity
```

A one-page explanation of how the algorithm works (trie, construction of failure links, finding several patterns
at once, complexity) is in Aho–Corasick explanation.txt

## How to run

Requirements: JDK 17+ (records are used). No external libraries.

```bash
./run.sh test    # compile and run the test suite
./run.sh bench   # compile and run the benchmark, writes results/results.{csv,md}
./run.sh         # both
```

`run.sh` simply does `javac -d out $(find src -name '*.java')` and then `java -cp out <MainClass>`.

## Implementation

### 1. Trie construction

All patterns are inserted into a trie. Each node corresponds to a prefix of at least one pattern;
the root is the empty string. Patterns that share a prefix share the corresponding path, so the
trie has at most `L + 1` nodes, where `L` is the total length of all patterns.
A node stores the ids of patterns that end exactly at it (`endsHere`), which also handles duplicate patterns.

Edges are stored as `int[σ]` arrays per node (`children[node][symbol]`), where `σ` is the number of
**distinct characters that occur in the patterns**. Characters are first mapped to compact indices
`0..σ-1` (alphabet compression). A text character that does not occur in any pattern cannot be part of
any match, so the search simply returns to the root on such a character.

### 2. Failure links

`fail[v]` points to the node representing the **longest proper suffix** of `v`'s string that is also
a node of the trie. It answers the question: *"the current partial match just broke — what is the longest
part of what I have already read that can still be the beginning of some pattern?"*

Failure links are built by **BFS**, so all nodes of smaller depth already have their links when a node is processed:

* children of the root fail to the root;
* for an edge `u --c--> v`: start from `f = fail[u]` and follow failure links until a node with an edge `c`
  is found (or the root is reached). Then `fail[v] = child(f, c)`, or the root if no such edge exists.

This is exactly the KMP prefix function generalised from one string to a tree of strings.

### 3. Output links

When the automaton is in node `v`, every pattern that is a **suffix** of `v`'s string ends at the current
text position — not only the pattern ending at `v` itself. All such patterns lie on the failure chain of `v`.
To avoid walking the whole chain through non-terminal nodes, each node has
`outLink[v]` = the nearest node on the failure chain where some pattern ends:

```
outLink[v] = fail[v]           if some pattern ends at fail[v]
           = outLink[fail[v]]  otherwise
```

Reporting at position `i` is then: report `endsHere[v]` (if any), then jump along `outLink` and report each
node's patterns. Every jump yields at least one match, so reporting costs `O(number of matches)`.

### 4. Search

```
state = root
for each position i in text:
    while state != root and state has no edge text[i]: state = fail[state]
    state = edge(state, text[i]) or root
    report patterns at state and along its outLink chain   (start = i - |pattern| + 1)
```

### Example: patterns `he, she, his, hers`, text `ushers`

| node | fail | outLink | patterns ending here |
|---|---|---|---|
| h | root | – | |
| he | root | – | he |
| her | root | – | |
| hers | s | – | hers |
| hi | root | – | |
| his | s | – | his |
| s | root | – | |
| sh | h | – | |
| she | he | he | she |

Scan of `ushers`: `u` → root, `s` → s, `h` → sh, `e` → she: report **she@1**, then outLink → **he@2**.
`r`: `she` has no `r` edge → fail to `he` → `her`. `s` → `hers`: report **hers@2**.
The `he` match is found only thanks to the output link, and the `hers` match only thanks to the failure link —
the characters `he` are never re-read.

## Complexity analysis

Notation: `n` – text length, `k` – number of patterns, `L` – total pattern length,
`m` – length of one pattern, `σ` – alphabet size (distinct pattern characters), `z` – number of matches reported.

### Aho–Corasick

| Phase | Time | Space |
|---|---|---|
| Alphabet compression | `O(L)` | `O(max char code)` |
| Trie construction | `O(L · σ)` (each new node allocates an `int[σ]` row) | `O(L · σ)` |
| Failure + output links | `O(L · σ)` | `O(L)` |
| Search | `O(n + z)` | `O(1)` extra (plus `O(z)` if matches are stored) |
| **Total** | **`O(L · σ + n + z)`** | **`O(L · σ)`** |

**Why the failure-link loops are linear (amortised argument).**
*Search:* the depth of the current state grows by at most 1 per text character and every failure transition
decreases it by at least 1. Depth never goes below 0, so the total number of failure transitions over the
whole text is at most `n`. *Construction:* the same argument applied along each pattern's path shows that the
`while` loops over failure links take `O(L)` in total; the `O(L · σ)` term comes from scanning/allocating the
`σ` child slots per node.

**Output-sensitivity.** The `z` term is unavoidable — every match must be reported — but it can be large:
for patterns `a, aa, ..., a^k` and text `a^n`, `z = Θ(n · k)`.
Output links guarantee that we pay only for real matches, never for empty steps along the failure chain.

**Space trade-off.** Array rows give `O(1)` transitions but use `O(L · σ)` memory. With a `HashMap` per node
the space drops to `O(L)` at the cost of a larger constant factor per transition; for a large alphabet
(e.g. full Unicode without compression) that is the better choice. Another variant precomputes the full
transition function (a DFA) so that the search loop has no `while` at all — same asymptotics, smaller constant.

### KMP run separately for each pattern

| Phase | Time | Space |
|---|---|---|
| Prefix function for one pattern | `O(m)` | `O(m)` |
| Search for one pattern | `O(n + m)` | `O(1)` extra |
| **All k patterns** | **`O(k · n + L)`** | `O(max m)` (one `π` array at a time) |

The text is scanned **k times**, so the cost grows linearly with the number of patterns.

### Why Aho–Corasick suits multiple patterns

* The text is scanned **once**, regardless of `k`: the search cost is `O(n + z)` instead of `O(k · n)`.
* The trie stores common prefixes only once, and failure links let the automaton reuse every character already
  read — it never moves backwards in the text, so it also works on streams.
* The cost of `k` moves into a one-time preprocessing step (`O(L · σ)`) that can be reused for many texts.

## Experimental results

Setup (`Benchmark.java`):

* Grid: **10, 100, 1 000 patterns** × **1 000, 10 000, 100 000 text characters**, for two alphabets:
  26 lowercase letters and the 4-letter DNA alphabet `ACGT` (smaller alphabet → many more matches).
* Pattern lengths uniform in `[4, 12]`; half of the patterns are substrings of the text (guaranteed matches),
  half are random strings. Fixed random seed `42`, so every run uses identical inputs.
* Every configuration is first run 3 times as JIT warm-up (not measured), then measured **5 times** with
  `System.nanoTime()`; the tables show the **average**.
* AC time is split into `build` (trie + links) and `search`; KMP time includes computing all prefix functions.
* Both algorithms count matches through the same callback interface; the benchmark checks that both counts are
  identical for every configuration.

Environment: Java 26.0.2.1, Mac OS X aarch64, 10 logical CPUs. All times in **milliseconds**.

### Alphabet: lowercase a–z (σ = 26)

| Patterns | Text length | Trie nodes | Matches | AC build | AC search | AC total | KMP × k | KMP / AC |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1,000 | 77 | 5 | 0.007 | 0.004 | 0.012 | 0.013 | 1.1x |
| 100 | 1,000 | 678 | 50 | 0.069 | 0.005 | 0.073 | 0.123 | 1.7x |
| 1000 | 1,000 | 5,938 | 501 | 0.765 | 0.015 | 0.781 | 1.225 | 1.6x |
| 10 | 10,000 | 94 | 5 | 0.008 | 0.031 | 0.039 | 0.122 | 3.1x |
| 100 | 10,000 | 691 | 50 | 0.042 | 0.035 | 0.078 | 1.228 | 15.8x |
| 1000 | 10,000 | 6,304 | 502 | 0.570 | 0.086 | 0.656 | 12.158 | 18.5x |
| 10 | 100,000 | 84 | 5 | 0.006 | 0.406 | 0.412 | 1.245 | 3.0x |
| 100 | 100,000 | 707 | 55 | 0.050 | 0.392 | 0.442 | 12.384 | 28.0x |
| 1000 | 100,000 | 6,488 | 530 | 0.704 | 0.708 | 1.412 | 123.660 | 87.6x |

### Alphabet: DNA ACGT (σ = 4)

| Patterns | Text length | Trie nodes | Matches | AC build | AC search | AC total | KMP × k | KMP / AC |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1,000 | 76 | 9 | 0.006 | 0.005 | 0.011 | 0.019 | 1.8x |
| 100 | 1,000 | 560 | 120 | 0.049 | 0.007 | 0.056 | 0.183 | 3.3x |
| 1000 | 1,000 | 3,729 | 1,163 | 0.364 | 0.015 | 0.380 | 1.607 | 4.2x |
| 10 | 10,000 | 66 | 72 | 0.004 | 0.064 | 0.068 | 0.271 | 4.0x |
| 100 | 10,000 | 552 | 667 | 0.045 | 0.088 | 0.133 | 2.418 | 18.2x |
| 1000 | 10,000 | 3,956 | 5,996 | 0.391 | 0.163 | 0.554 | 23.977 | 43.3x |
| 10 | 100,000 | 65 | 815 | 0.006 | 0.771 | 0.777 | 2.742 | 3.5x |
| 100 | 100,000 | 572 | 7,423 | 0.055 | 0.920 | 0.976 | 28.580 | 29.3x |
| 1000 | 100,000 | 4,031 | 57,494 | 0.435 | 1.491 | 1.926 | 285.726 | 148.3x |

Raw numbers: [`results/results.csv`](results/results.csv). Absolute times depend on the machine; re-run
`./run.sh bench` to reproduce on your own hardware.

## Discussion of the results

**KMP scales linearly with the number of patterns.** For a 100 000-character text (a–z), KMP takes
1.2 → 12 → 124 ms for 10 → 100 → 1 000 patterns: roughly ×10 for every ×10 patterns, exactly as `O(k · n)` predicts.

**Aho–Corasick search time barely depends on the number of patterns.** On the same text the AC search takes
0.41 → 0.39 → 0.71 ms. The text is scanned once; the remaining growth comes from (a) more matches `z` and
(b) a larger automaton that no longer fits in the CPU caches as well (6 488 nodes × 26 slots ≈ 0.67 MB of edge arrays).

**The match count `z` matters.** On DNA the same grid produces up to 57 494 matches (vs 530 on a–z), and the
AC search time roughly doubles (0.71 → 1.49 ms) — the output-sensitive `+ z` term in `O(n + z)` is visible.

**Preprocessing is the price of Aho–Corasick.** For 1 000 patterns the build takes 0.4–0.8 ms. On a short text
(1 000 chars, a–z) the build is ~98 % of AC's total time and the advantage shrinks to 1.6x. The build is also
more expensive for σ = 26 than for σ = 4 because each node allocates an `int[σ]` row — the `O(L · σ)` term from the
analysis. The build is done once and can be reused for any number of texts, so in practice (scanning many
documents / a stream) it is amortised away.

**Small cases.** With 10 patterns on 1 000 characters both algorithms finish in ~0.01–0.02 ms; the difference
is close to the measurement noise. Timings below ~0.1 ms are sensitive to JIT, GC and timer resolution, so they
should be read as "comparable", not as precise ratios.

### Advantages and limitations

**Aho–Corasick**

* ✅ One pass over the text: `O(n + z)` search, independent of the number of patterns.
* ✅ Finds all overlapping occurrences of all patterns; works on streaming input (never moves back in the text).
* ✅ Automaton is built once and reused for many texts.
* ❌ Preprocessing time and memory: `O(L · σ)` with array rows; noticeably larger than KMP's `O(m)`.
* ❌ More complex to implement correctly (failure links, output links, BFS order).
* ❌ For one or very few patterns on a short text the build overhead outweighs the benefit.
* ❌ Patterns are fixed after construction — adding a pattern requires rebuilding the links.

**KMP (per pattern)**

* ✅ Very simple, tiny memory footprint (`O(m)` per pattern), cache-friendly tight loop.
* ✅ Optimal `O(n + m)` for a **single** pattern; adding/removing patterns is trivial.
* ❌ Re-scans the whole text for every pattern: `O(k · n + L)`; becomes impractical for hundreds or thousands of
  patterns (124–286 ms vs 1.4–1.9 ms here for 1 000 patterns on 100 000 characters).
* ❌ Shares no work between patterns with common prefixes.

**Conclusion.** For a single pattern KMP is the natural choice. As soon as many patterns must be found in the same
text — keyword filtering, intrusion-detection signatures, DNA motif search, dictionary matching —
Aho–Corasick wins by one to two orders of magnitude, and the gap grows with both the number of patterns and the
text length.
