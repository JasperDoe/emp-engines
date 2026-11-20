package dev.empengines.researchenchanting.table;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The Echo minigame that binds a book: the table lights its three buttons in a sequence (each with its own tone),
 * and the player repeats it. Three rounds, each one step longer (3, 4, 5). A wrong press costs a lapis and
 * replays the round. More books on the shelves around the table play the sequence slower.
 * <p>
 * Server only. The client plays the sequence back from the menu's data slots; {@link #playId} goes up every
 * time it should start (again), so the screen knows to replay.
 */
public final class Echo {
    public static final int IDLE = 0, PLAYING = 1;
    public static final int MAX_LEN = 8;
    public enum Result { NOTHING, STARTED, RIGHT, ROUND_DONE, WRONG, SOLVED }

    public final ResourceLocation focus;
    public final int rounds;
    public final int stepTicks;
    private final int startLen;
    private final RandomSource random;
    private final int[] seq = new int[MAX_LEN];
    private int round;        // 1-based once started
    private int pos;
    private int playId;
    private int phase = IDLE;

    public Echo(ResourceLocation focus, int rounds, int startLen, int stepTicks, RandomSource random) {
        this.focus = focus;
        this.rounds = rounds;
        this.startLen = startLen;
        this.stepTicks = stepTicks;
        this.random = random;
    }

    public int phase() { return phase; }
    public int round() { return round; }
    public int length() { return round == 0 ? 0 : Math.min(MAX_LEN, startLen + round - 1); }
    public int pos() { return pos; }
    public int playId() { return playId; }
    public int step(int i) { return seq[i]; }

    /** The player pressed a button. */
    public Result press(int button) {
        if (phase == IDLE) {
            round = 1;
            newSequence();
            phase = PLAYING;
            return Result.STARTED;
        }
        if (button != seq[pos]) {
            pos = 0;
            playId++;
            return Result.WRONG;
        }
        if (++pos < length()) return Result.RIGHT;
        if (round >= rounds) return Result.SOLVED;
        round++;
        newSequence();
        return Result.ROUND_DONE;
    }

    /** A new sequence for this round: the last round's steps plus a new one, Simon-style. */
    private void newSequence() {
        int len = length();
        for (int i = Math.max(0, len - (round == 1 ? len : 1)); i < len; i++) seq[i] = random.nextInt(3);
        pos = 0;
        playId++;
    }
}
