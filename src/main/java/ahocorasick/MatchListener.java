package ahocorasick;

/**
 * Callback invoked for every match. Using a callback instead of always building a list lets the
 * benchmark measure the matching itself, without the cost of allocating millions of objects.
 */
@FunctionalInterface
public interface MatchListener {
    void onMatch(int start, int patternIndex);
}
