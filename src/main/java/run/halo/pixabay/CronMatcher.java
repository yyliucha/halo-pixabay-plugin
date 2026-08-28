package run.halo.pixabay;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal 5-field cron expression matcher (minute hour day-of-month month day-of-week).
 *
 * <p>Supports {@code *}, lists ({@code a,b}), ranges ({@code a-b}) and step
 * values (e.g. {@code star/5} or {@code a-b/n}). When both day-of-month and
 * day-of-week are restricted, the standard cron OR semantics apply (a day
 * matches if either field matches).</p>
 */
public class CronMatcher {

    private static final int[] LIMITS = {59, 23, 31, 12, 7};

    private final Set<Integer>[] fields = new Set[5];
    private final boolean[] wildcard = new boolean[5];

    private CronMatcher() {
    }

    public static CronMatcher parse(String expression) {
        CronMatcher matcher = new CronMatcher();
        String[] parts = expression.trim().split("\\s+");
        // Accept Quartz-style 6-field expressions (sec min hour dom month dow):
        // drop the seconds field. '?' (not-specified) is treated as '*'.
        if (parts.length == 6) {
            parts = java.util.Arrays.copyOfRange(parts, 1, 6);
        } else if (parts.length != 5) {
            throw new IllegalArgumentException(
                "cron expression must have 5 fields (min hour dom month dow): " + expression);
        }
        for (int i = 0; i < 5; i++) {
            String field = parts[i].equals("?") ? "*" : parts[i];
            matcher.fields[i] = parseField(field, LIMITS[i]);
            matcher.wildcard[i] = field.equals("*");
        }
        return matcher;
    }

    private static Set<Integer> parseField(String field, int max) {
        Set<Integer> values = new HashSet<>();
        for (String item : field.split(",")) {
            String range = item;
            int step = 1;
            int slash = item.indexOf('/');
            if (slash >= 0) {
                range = item.substring(0, slash);
                step = Integer.parseInt(item.substring(slash + 1));
                if (step < 1) {
                    throw new IllegalArgumentException("invalid step in '" + field + "'");
                }
            }
            int from;
            int to;
            int dash = range.indexOf('-');
            if (range.equals("*")) {
                from = 0;
                to = max;
            } else if (dash >= 0) {
                from = Integer.parseInt(range.substring(0, dash));
                to = Integer.parseInt(range.substring(dash + 1));
            } else {
                from = Integer.parseInt(range);
                to = from;
            }
            if (from < 0 || to > max || from > to) {
                throw new IllegalArgumentException("out of range in '" + field + "'");
            }
            for (int v = from; v <= to; v += step) {
                values.add(v);
            }
        }
        return values;
    }

    /**
     * Whether the given time matches this cron expression.
     */
    public boolean matches(ZonedDateTime t) {
        if (!fields[0].contains(t.getMinute())) {
            return false;
        }
        if (!fields[1].contains(t.getHour())) {
            return false;
        }
        if (!fields[3].contains(t.getMonthValue())) {
            return false;
        }
        boolean domMatches = fields[2].contains(t.getDayOfMonth());
        boolean dowMatches = fields[4].contains(t.getDayOfWeek().getValue() % 7)
            || fields[4].contains(7);
        if (wildcard[2] && wildcard[4]) {
            return true;
        }
        if (wildcard[2]) {
            return dowMatches;
        }
        if (wildcard[4]) {
            return domMatches;
        }
        return domMatches || dowMatches;
    }
}
