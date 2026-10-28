package io.github.adang101.alpineshred.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every movement number in one place (design doc section 7, "Tuning file").
 * Loaded from {@code data/alpineshred/movement/tuning.json}, synced to clients, and reloadable.
 *
 * <p>Units: speed in blocks per tick (1.0 = 20 blocks per second), acceleration in blocks per
 * tick squared, angles in degrees, durations in ticks.
 *
 * <p>Keys beyond the doc's table are marked "ext" below. They make constants that the doc fixes in
 * prose (board vs skis multipliers, wax, air rotation rates) tunable through the same file.
 */
public final class MovementTuning {

    /** One tunable key: default and inclusive valid range. */
    public record Spec(double defaultValue, double min, double max, String description) {
    }

    public static final Map<String, Spec> SPECS;

    static {
        Map<String, Spec> s = new LinkedHashMap<>();
        // Section 7 tuning table
        s.put("slopeAccelScale", new Spec(0.35, 0.0, 2.0, "Fraction of vanilla gravity applied along slopes"));
        s.put("dragCoeff", new Spec(0.005, 0.0, 0.1, "Drag deceleration = coefficient x speed squared"));
        s.put("maxSpeed", new Spec(1.40, 0.1, 3.0, "Speed cap before the board multiplier"));
        s.put("baseTurnRate", new Spec(7.0, 0.0, 45.0, "Degrees per tick at zero speed"));
        s.put("turnSpeedRef", new Spec(0.45, 0.01, 5.0, "Speed at which turn rate halves"));
        s.put("gripBlend", new Spec(0.35, 0.02, 1.0, "Velocity-to-heading blend at grip 1.0"));
        s.put("brakeFrictionMul", new Spec(8.0, 1.0, 50.0, "Brake friction multiplier (board)"));
        s.put("tuckDragMul", new Spec(0.8, 0.1, 2.0, "W drag multiplier"));
        s.put("flareDragMul", new Spec(1.6, 0.5, 10.0, "S drag multiplier"));
        s.put("popVelocity", new Spec(0.42, 0.0, 2.0, "Base pop"));
        s.put("popChargeMax", new Spec(0.6, 0.0, 3.0, "Maximum charge bonus"));
        s.put("snapDown", new Spec(1.0, 0.0, 2.0, "Ground snap distance"));
        s.put("launchGrade", new Spec(0.35, 0.05, 3.0, "Ramp launch grade"));
        s.put("stepUp", new Spec(1.0, 0.6, 1.5, "Step height while riding"));
        s.put("poleImpulse", new Spec(0.10, 0.0, 1.0, "Push speed gain"));
        s.put("powderFloatSpeed", new Spec(0.25, 0.0, 3.0, "Minimum speed to float on powder"));
        s.put("wallHitSpeed", new Spec(0.5, 0.0, 5.0, "Minimum head-on speed for a wipeout"));
        s.put("wipeoutTicks", new Spec(30, 0, 200, "Input lock duration"));
        s.put("strappedWalkMod", new Spec(-0.35, -1.0, 0.0, "Walk speed modifier when strapped off snow"));
        // Section 7 steering keys
        s.put("edgeTurnBoost", new Spec(0.5, 0.0, 5.0, "Extra turn rate when the held edge matches the turn"));
        s.put("edgeScrub", new Spec(0.0015, 0.0, 0.1, "Speed scrubbed per tick while an edge key is held"));
        s.put("counterEdgeGrip", new Spec(0.6, 0.02, 1.0, "Grip multiplier when holding the opposite edge (drift)"));
        s.put("lookDeadzone", new Spec(1.5, 0.0, 45.0, "Look-to-steer dead zone"));
        s.put("pivotSpeed", new Spec(0.1, 0.0, 1.0, "Below this speed A and D pivot the board"));
        s.put("pivotRate", new Spec(6.0, 0.0, 45.0, "Pivot degrees per tick"));
        // ext: constants the doc states in prose
        s.put("skiBrakeFrictionMul", new Spec(6.0, 1.0, 50.0, "ext: brake friction multiplier on skis"));
        s.put("skiBrakeSteerMul", new Spec(0.5, 0.0, 1.0, "ext: steering multiplier while snowplowing"));
        s.put("boardTurnMul", new Spec(0.9, 0.1, 5.0, "ext: board turn-rate multiplier"));
        s.put("skiTurnMul", new Spec(1.1, 0.1, 5.0, "ext: ski turn-rate multiplier"));
        s.put("slipLoss", new Spec(0.25, 0.0, 1.0, "ext: speed lost to sideways slip, x (1 - cos slip)"));
        s.put("popChargeTicks", new Spec(10, 1, 40, "ext: ticks of jump hold for a full charge"));
        s.put("launchMinSpeed", new Spec(0.45, 0.0, 3.0, "ext: minimum speed for a ramp launch"));
        s.put("launchVyFactor", new Spec(0.9, 0.0, 2.0, "ext: vy = factor x speed x grade at launch"));
        s.put("stepUpMinSpeed", new Spec(0.1, 0.0, 3.0, "ext: minimum speed for the raised step height"));
        s.put("poleCooldownTicks", new Spec(12, 0, 100, "ext: ticks between pole pushes"));
        s.put("poleMaxSpeed", new Spec(0.6, 0.0, 3.0, "ext: pole push works only below this speed"));
        s.put("wipeoutSpeedBleed", new Spec(0.10, 0.0, 1.0, "ext: fraction of speed lost per wipeout tick"));
        s.put("wipeoutDamagePerSpeed", new Spec(8.0, 0.0, 100.0, "ext: damage = (impact - wallHitSpeed) x this"));
        s.put("helmetWipeoutReduction", new Spec(0.4, 0.0, 1.0, "ext: helmet damage reduction"));
        s.put("waxFrictionMul", new Spec(0.85, 0.1, 1.0, "ext: friction multiplier while waxed"));
        s.put("waxSpeedMul", new Spec(1.08, 1.0, 2.0, "ext: speed multiplier while waxed"));
        s.put("gradeSmoothing", new Spec(0.3, 0.01, 1.0, "ext: exponential smoothing alpha for slope grade"));
        s.put("airControl", new Spec(0.0, 0.0, 1.0, "ext: air steering (0 = none)"));
        s.put("airSpinMaxBoard", new Spec(24.0, 0.0, 90.0, "ext: max spin rate on a board, degrees per tick"));
        s.put("airSpinMaxSkis", new Spec(28.0, 0.0, 90.0, "ext: max spin rate on skis, degrees per tick"));
        s.put("airFlipMax", new Spec(24.0, 0.0, 90.0, "ext: max flip rate, degrees per tick"));
        s.put("airRampTicks", new Spec(4, 1, 20, "ext: ticks for rotation to ramp from 0 to max"));
        s.put("airGrabRotationMul", new Spec(0.8, 0.0, 1.0, "ext: rotation rate multiplier while grabbing"));
        SPECS = Collections.unmodifiableMap(s);
    }

    public static final MovementTuning DEFAULT = new MovementTuning(Map.of());

    public final double slopeAccelScale;
    public final double dragCoeff;
    public final double maxSpeed;
    public final double baseTurnRate;
    public final double turnSpeedRef;
    public final double gripBlend;
    public final double brakeFrictionMul;
    public final double tuckDragMul;
    public final double flareDragMul;
    public final double popVelocity;
    public final double popChargeMax;
    public final double snapDown;
    public final double launchGrade;
    public final double stepUp;
    public final double poleImpulse;
    public final double powderFloatSpeed;
    public final double wallHitSpeed;
    public final int wipeoutTicks;
    public final double strappedWalkMod;
    public final double edgeTurnBoost;
    public final double edgeScrub;
    public final double counterEdgeGrip;
    public final double lookDeadzone;
    public final double pivotSpeed;
    public final double pivotRate;
    public final double skiBrakeFrictionMul;
    public final double skiBrakeSteerMul;
    public final double boardTurnMul;
    public final double skiTurnMul;
    public final double slipLoss;
    public final int popChargeTicks;
    public final double launchMinSpeed;
    public final double launchVyFactor;
    public final double stepUpMinSpeed;
    public final int poleCooldownTicks;
    public final double poleMaxSpeed;
    public final double wipeoutSpeedBleed;
    public final double wipeoutDamagePerSpeed;
    public final double helmetWipeoutReduction;
    public final double waxFrictionMul;
    public final double waxSpeedMul;
    public final double gradeSmoothing;
    public final double airControl;
    public final double airSpinMaxBoard;
    public final double airSpinMaxSkis;
    public final double airFlipMax;
    public final int airRampTicks;
    public final double airGrabRotationMul;

    private final Map<String, Double> values;

    /** @param overrides already-validated values by key; missing keys take their defaults */
    private MovementTuning(Map<String, Double> overrides) {
        Map<String, Double> v = new LinkedHashMap<>();
        for (Map.Entry<String, Spec> e : SPECS.entrySet()) {
            v.put(e.getKey(), overrides.getOrDefault(e.getKey(), e.getValue().defaultValue()));
        }
        this.values = Collections.unmodifiableMap(v);
        slopeAccelScale = get("slopeAccelScale");
        dragCoeff = get("dragCoeff");
        maxSpeed = get("maxSpeed");
        baseTurnRate = get("baseTurnRate");
        turnSpeedRef = get("turnSpeedRef");
        gripBlend = get("gripBlend");
        brakeFrictionMul = get("brakeFrictionMul");
        tuckDragMul = get("tuckDragMul");
        flareDragMul = get("flareDragMul");
        popVelocity = get("popVelocity");
        popChargeMax = get("popChargeMax");
        snapDown = get("snapDown");
        launchGrade = get("launchGrade");
        stepUp = get("stepUp");
        poleImpulse = get("poleImpulse");
        powderFloatSpeed = get("powderFloatSpeed");
        wallHitSpeed = get("wallHitSpeed");
        wipeoutTicks = (int) Math.round(get("wipeoutTicks"));
        strappedWalkMod = get("strappedWalkMod");
        edgeTurnBoost = get("edgeTurnBoost");
        edgeScrub = get("edgeScrub");
        counterEdgeGrip = get("counterEdgeGrip");
        lookDeadzone = get("lookDeadzone");
        pivotSpeed = get("pivotSpeed");
        pivotRate = get("pivotRate");
        skiBrakeFrictionMul = get("skiBrakeFrictionMul");
        skiBrakeSteerMul = get("skiBrakeSteerMul");
        boardTurnMul = get("boardTurnMul");
        skiTurnMul = get("skiTurnMul");
        slipLoss = get("slipLoss");
        popChargeTicks = (int) Math.round(get("popChargeTicks"));
        launchMinSpeed = get("launchMinSpeed");
        launchVyFactor = get("launchVyFactor");
        stepUpMinSpeed = get("stepUpMinSpeed");
        poleCooldownTicks = (int) Math.round(get("poleCooldownTicks"));
        poleMaxSpeed = get("poleMaxSpeed");
        wipeoutSpeedBleed = get("wipeoutSpeedBleed");
        wipeoutDamagePerSpeed = get("wipeoutDamagePerSpeed");
        helmetWipeoutReduction = get("helmetWipeoutReduction");
        waxFrictionMul = get("waxFrictionMul");
        waxSpeedMul = get("waxSpeedMul");
        gradeSmoothing = get("gradeSmoothing");
        airControl = get("airControl");
        airSpinMaxBoard = get("airSpinMaxBoard");
        airSpinMaxSkis = get("airSpinMaxSkis");
        airFlipMax = get("airFlipMax");
        airRampTicks = (int) Math.round(get("airRampTicks"));
        airGrabRotationMul = get("airGrabRotationMul");
    }

    /** Value by key; throws for unknown keys so typos fail loudly in tests. */
    public double get(String key) {
        Double value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown tuning key: " + key);
        return value;
    }

    public Map<String, Double> values() {
        return values;
    }

    /** Vanilla gravity scaled for slopes and friction: {@code 0.08 * slopeAccelScale}. */
    public double effectiveGravity() {
        return SlideModel.VANILLA_GRAVITY * slopeAccelScale;
    }

    /**
     * Builds tuning from a JSON object. Unknown keys and invalid values are reported through
     * {@code warn}; invalid values fall back to defaults and out-of-range values are clamped.
     */
    public static MovementTuning fromJson(JsonObject json, Consumer<String> warn) {
        Map<String, Double> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_") || key.equals("comment")) continue;
            Spec spec = SPECS.get(key);
            if (spec == null) {
                warn.accept("tuning: unknown key '" + key + "' ignored");
                continue;
            }
            double value;
            try {
                value = entry.getValue().getAsDouble();
            } catch (RuntimeException e) {
                warn.accept("tuning: '" + key + "' is not a number, using default " + spec.defaultValue());
                continue;
            }
            if (!Double.isFinite(value)) {
                warn.accept("tuning: '" + key + "' is not finite, using default " + spec.defaultValue());
                continue;
            }
            if (value < spec.min() || value > spec.max()) {
                double clamped = Math.max(spec.min(), Math.min(spec.max(), value));
                warn.accept("tuning: '" + key + "' = " + value + " is outside [" + spec.min() + ", "
                        + spec.max() + "], clamped to " + clamped);
                value = clamped;
            }
            overrides.put(key, value);
        }
        return new MovementTuning(overrides);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    /** Builds a copy with some values replaced, validated the same way as the file. */
    public MovementTuning with(Map<String, Double> changes, Consumer<String> warn) {
        JsonObject json = toJson();
        changes.forEach(json::addProperty);
        return fromJson(json, warn);
    }
}
