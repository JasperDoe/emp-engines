package dev.empengines.researchenchanting.table;

import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.ResearchData;

/**
 * Tree rules shared by the server (starting research) and the screen (drawing the tree). A book holds one
 * enchantment. Its next level is always open; level I of another line opens if that line is a root, or if the
 * book holds the line's parent at the level the branch needs. Starting another line replaces what the book held.
 */
public final class Research {
    public enum State { RESEARCHED, ACTIVE, AVAILABLE, LOCKED }

    public static boolean available(ResearchData d, Focus.Line line, int level) {
        if (level < 1 || level > line.max()) return false;
        if (d.holds(line.enchantment())) return level == d.level() + 1;
        if (level != 1) return false;
        return line.requires().map(r -> d.holds(r.enchantment()) && d.level() >= r.level()).orElse(true);
    }

    /** Starting this line would replace the enchantment the book holds now. */
    public static boolean replaces(ResearchData d, Focus.Line line) {
        return d.hasResearch() && !d.holds(line.enchantment());
    }

    public static State state(ResearchData d, Focus.Line line, int level) {
        if (d.holds(line.enchantment()) && d.level() >= level) return State.RESEARCHED;
        if (d.active().map(a -> a.enchantment().equals(line.enchantment()) && a.level() == level).orElse(false)) return State.ACTIVE;
        return available(d, line, level) ? State.AVAILABLE : State.LOCKED;
    }

    private Research() {}
}
