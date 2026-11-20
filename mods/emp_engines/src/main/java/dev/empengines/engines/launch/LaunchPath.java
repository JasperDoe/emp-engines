package dev.empengines.engines.launch;

/**
 * The scripted path the ship follows during a launch, shared by the server (which moves the
 * ship) and the client (which draws the effects), so both agree to the tick and the motion is smooth.
 * Offsets are in blocks above the ship's starting height.
 *
 * The launch is thrown hard: the ship leaves the platform at twice its average climbing speed and is
 * still going fast when it breaks through the clouds. Then it coasts, slowing exponentially but never
 * quite stopping, and the white-out comes during the coast.
 */
public final class LaunchPath {
    public static final int COUNTDOWN = 100, BEAMS = 80, SPIN = 80;
    /** Ticks of the thrown climb, and of the coast above the clouds that follows it. */
    public static final int RISE = 70, DRIFT = 60, LAUNCH = RISE + DRIFT;
    /** Ticks at the end of the coast that fade the crew's view to white. */
    public static final int WHITEOUT = 35;
    public static final double LIFT = 3.0;
    /** How quickly the coast slows (ticks for the speed to drop to about a third). */
    public static final double TAU = 30;
    /** Start and end speed of the climb, as multiples of its average speed. */
    private static final double START = 2.0, END = 0.5;
    /** How far the coast carries the ship, as a fraction of the climb. */
    public static final double COAST = END / RISE * TAU * (1 - Math.exp(-DRIFT / TAU));

    public static double easeInOut(double t) {
        t = clamp(t);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    public static double easeIn(double t) {
        t = clamp(t);
        return t * t * t;
    }

    public static double easeOutQuart(double t) {
        t = clamp(t);
        return 1 - Math.pow(1 - t, 4);
    }

    /** The climb from 0 to 1: leaves at START times the average speed and ends at END times it, always slowing. */
    static double climbCurve(double s) {
        s = clamp(s);
        double a = START + END - 2, b = 3 - 2 * START - END;
        return a * s * s * s + b * s * s + START * s;
    }

    /**
     * Height above the start for a phase (0 = countdown ... 3 = launch) at tick t.
     * {@code climb} is how far the thrown part of the launch carries the ship (set when the launch starts).
     */
    public static double offset(int phase, double t, double climb) {
        return switch (phase) {
            case 0 -> LIFT * easeInOut(t / COUNTDOWN);
            case 1 -> LIFT + 0.12 * Math.sin(t * 0.15);
            case 2 -> LIFT + 0.12 * Math.sin(t * 0.15) + 0.04 * Math.sin(t * 2.7) * (t / SPIN);
            case 3 -> {
                if (t <= RISE) yield LIFT + climb * climbCurve(t / RISE);
                double v1 = END * climb / RISE;   // blocks per tick at the end of the climb
                yield LIFT + climb + v1 * TAU * (1 - Math.exp(-(t - RISE) / TAU));
            }
            default -> 0;
        };
    }

    /** Speed in blocks per tick during the launch. */
    public static double speed(double t, double climb) {
        return offset(3, t + 0.5, climb) - offset(3, Math.max(0, t - 0.5), climb);
    }

    /** The largest climb that keeps the whole launch (climb plus coast) within {@code room} blocks. */
    public static double climbFor(double room) { return room / (1 + COAST); }

    /** Tick of the launch at which the ship reaches {@code height} blocks above its start (or -1 if never). */
    public static double tickAtHeight(double height, double climb) {
        if (height <= LIFT) return 0;
        if (height > offset(3, LAUNCH, climb)) return -1;
        double lo = 0, hi = LAUNCH;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (offset(3, mid, climb) < height) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    private static double clamp(double t) { return Math.max(0, Math.min(1, t)); }

    private LaunchPath() {}
}
