package ahocorasick;

/**
 * One occurrence of a pattern in the text.
 *
 * @param start        index in the text where the occurrence begins (0-based)
 * @param patternIndex index of the pattern in the array passed to the matcher
 */
public record Match(int start, int patternIndex) implements Comparable<Match> {

    @Override
    public int compareTo(Match other) {
        if (start != other.start) {
            return Integer.compare(start, other.start);
        }
        return Integer.compare(patternIndex, other.patternIndex);
    }
}
