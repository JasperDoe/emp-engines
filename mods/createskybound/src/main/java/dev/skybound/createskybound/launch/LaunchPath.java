package dev.skybound.createskybound.launch;

/**
 * The scripted path the ship follows during a launch, shared by the server (which moves the
 * ship) and the client (which draws the effects), so both agree to the tick and the motion is smooth.
 * Offsets are in blocks above the ship's starting height.
 *
 * The launch itself is thrown hard and eases out: the ship leaves the platform at full speed,
 * slows as it climbs through the cloud layer, then drifts gently above the clouds for a few
 * seconds before the white-out and the jump into the Chaotic Skies.
 */
public final class LaunchPath {
    public static final int COUNTDOWN = 100, BEAMS = 80, SPIN = 80;
    /** Ticks of the thrown climb, and of the slow drift above the clouds that follows it. */
    public static final int RISE = 70, DRIFT = 100, LAUNCH = RISE + DRIFT;
    /** Ticks at the end of the drift that fade the crew's view to white. */
    public static final int WHITEOUT = 35;
    public static final double LIFT = 3.0;
    /** How far the ship keeps floating up during the drift. */
    public static final double DRIFT_CLIMB = 6.0;

    public static double easeInOut(double t) {
        t = clamp(t);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    public static double easeIn(double t) {
        t = clamp(t);
        return t * t * t;
    }

    /** Fast start, long soft finish (speed is 4x the average at the start, zero at the end). */
    public static double easeOutQuart(double t) {
        t = clamp(t);
        return 1 - Math.pow(1 - t, 4);
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
                if (t <= RISE) yield LIFT + climb * easeOutQuart(t / RISE);
                double s = clamp((t - RISE) / DRIFT);
                yield LIFT + climb + DRIFT_CLIMB * s * s + 0.25 * Math.sin((t - RISE) * 0.08) * s;
            }
            default -> 0;
        };
    }

    /** Tick of the launch at which the ship reaches {@code height} blocks above its start (or -1 if never). */
    public static double tickAtHeight(double height, double climb) {
        double h = (height - LIFT) / climb;
        if (h <= 0) return 0;
        if (h >= 1) return -1;
        return RISE * (1 - Math.pow(1 - h, 0.25));
    }

    private static double clamp(double t) { return Math.max(0, Math.min(1, t)); }

    private LaunchPath() {}
}
