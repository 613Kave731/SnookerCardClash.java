package snooker;

/**
 * IGameRule — Strategy Pattern interface for pluggable rule sets.
 *
 * Dependency Inversion Principle (DIP):
 *   High-level modules (GameController, Player) depend on this abstraction,
 *   never on a concrete rule class. A rule is injected at construction time
 *   and neither the controller nor any player knows which variant is active.
 *
 * Both methods must be called BEFORE card.play() so they can inspect the
 * pre-play GameState flags (lastWasRed, freeBall, etc.) for their logic.
 */
public interface IGameRule {

    /**
     * Returns true if playing {@code card} is a legal move right now.
     * An invalid move causes the turn to be forfeited; the card is discarded.
     * Throws InvalidMoveException when the move violates a strict ordering rule
     * (e.g. clearance phase out-of-order), distinguishing it from a simple
     * invalid-sequence discard.
     */
    boolean isValid(Card card, GameState state) throws InvalidMoveException;

    /**
     * Returns the points this card is worth under the active rule set.
     * Called before card.play() so the pre-play state can inform bonuses.
     */
    int calculatePoints(Card card, GameState state);

    /**
     * How much stamina each card play costs this turn.
     * Default is 1; EnduranceModeRule doubles the drain to make stamina management critical.
     */
    default int getStaminaDrain() { return 1; }

    /**
     * Returns the foul penalty for illegally playing {@code playedCard}.
     * Real snooker rules: minimum penalty is 4; Blue (5), Pink (6), or Black (7)
     * carry their own ball value as the penalty instead.
     */
    default int calculateFoulPenalty(Card playedCard, GameState state) {
        if (playedCard instanceof ColourCard) {
            int pts = playedCard.getPoints();
            return pts >= 5 ? pts : 4;
        }
        return 4;
    }

    /**
     * Called by Player.playTurn() when isValid() returns false — after the card
     * has actually been chosen and played invalidly, NOT during AI filtering.
     * This keeps isValid() side-effect-free so the AI can safely call it on every
     * card in hand without accidentally setting pending fouls.
     * Default is a no-op; strict rules override to apply the correct foul.
     */
    default void onInvalidPlay(Card card, GameState state) {}
}

// =============================================================================

/**
 * ChaosModeRule — removes all sequencing restrictions.
 *
 * Any card is legal at any time. However, following the traditional
 * Red → Colour order is rewarded with double points as a skill bonus,
 * incentivising strategic play even without enforcement.
 */
class ChaosModeRule implements IGameRule {

    @Override
    public boolean isValid(Card card, GameState state) throws InvalidMoveException {
        return true;    // chaos: every card is always a legal play
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        boolean followsSequence =
            (card instanceof RedCard    && state.expectingRed())  ||
            (card instanceof ColourCard && state.lastPlayedRed());
        // Skill bonus: playing in the traditional sequence doubles the reward
        return followsSequence ? card.getPoints() * 2 : card.getPoints();
    }
}

// =============================================================================

/**
 * SnookerRule — strict simulation of real snooker ball sequencing.
 *
 * Standard phase (redsOnTable > 0):
 *   • Red must precede every Colour. Playing a Colour when a Red is required
 *     is a 7-point foul awarded to the opponent (stored as a pending foul in
 *     GameState so the controller can credit the correct player).
 *
 * Clearance phase (redsOnTable == 0):
 *   • No Reds may be played.
 *   • Colours must be potted in strict order: Yellow → Green → Brown → Blue → Pink → Black.
 *   • Playing out of order throws InvalidMoveException.
 *
 * Action cards are always legal regardless of phase.
 */
class SnookerRule implements IGameRule {

    static final String[] CLEARANCE_ORDER = {"Yellow", "Green", "Brown", "Blue", "Pink", "Black"};

    @Override
    public boolean isValid(Card card, GameState state) throws InvalidMoveException {
        if (card instanceof ActionCard) return true;

        if (state.isInClearanceMode()) {
            if (card instanceof RedCard) {
                throw new InvalidMoveException("No reds remain — clearance phase is active!");
            }
            if (card instanceof ColourCard) {
                int idx = state.getClearanceIndex();
                if (idx >= CLEARANCE_ORDER.length) {
                    throw new InvalidMoveException("All colours have been potted — frame is over!");
                }
                if (!card.getName().equals(CLEARANCE_ORDER[idx])) {
                    throw new InvalidMoveException(
                        "Clearance order violated! Expected: " + CLEARANCE_ORDER[idx]
                        + " but played: " + card.getName());
                }
                return true;
            }
        }

        // Standard phase
        if (card instanceof RedCard) {
            return state.expectingRed();
        }
        if (card instanceof ColourCard) {
            return state.lastPlayedRed();  // foul applied via onInvalidPlay, not here
        }

        return true;
    }

    @Override
    public void onInvalidPlay(Card card, GameState state) {
        if (card instanceof ColourCard && !state.lastPlayedRed() && !state.isInClearanceMode()) {
            state.setPendingFoul(calculateFoulPenalty(card, state), "illegal " + card.getName() + " play");
        }
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        return card.getPoints();
    }
}

// =============================================================================

/**
 * EnduranceModeRule — strict sequencing with high-value colour bonuses and doubled stamina drain.
 *
 * Enforces the same Red → Colour ordering as SnookerRule, including strict clearance
 * phase ordering (Yellow → Black). Stamina drains at 2 per turn instead of 1, making
 * resource management the central strategic challenge. Colours worth 5+ pts earn +2 bonus.
 */
class EnduranceModeRule implements IGameRule {

    @Override
    public boolean isValid(Card card, GameState state) throws InvalidMoveException {
        if (card instanceof ActionCard) return true;

        if (state.isInClearanceMode()) {
            if (card instanceof RedCard)
                throw new InvalidMoveException("No reds remain — clearance phase is active!");
            if (card instanceof ColourCard) {
                int idx = state.getClearanceIndex();
                if (idx >= SnookerRule.CLEARANCE_ORDER.length)
                    throw new InvalidMoveException("All colours potted — frame is over!");
                if (!card.getName().equals(SnookerRule.CLEARANCE_ORDER[idx]))
                    throw new InvalidMoveException(
                        "Clearance order violated! Expected: " + SnookerRule.CLEARANCE_ORDER[idx]
                        + " but played: " + card.getName());
                return true;
            }
        }

        if (card instanceof RedCard) return state.expectingRed();
        if (card instanceof ColourCard) {
            return state.lastPlayedRed();  // foul applied via onInvalidPlay, not here
        }
        return true;
    }

    @Override
    public void onInvalidPlay(Card card, GameState state) {
        if (card instanceof ColourCard && !state.lastPlayedRed() && !state.isInClearanceMode()) {
            state.setPendingFoul(calculateFoulPenalty(card, state), "illegal " + card.getName() + " play");
        }
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        int base = card.getPoints();
        return (card instanceof ColourCard && base >= 5) ? base + 2 : base;
    }

    @Override
    public int getStaminaDrain() { return 2; }
}

// =============================================================================

/**
 * ValidityPolicy — defines WHICH cards are legal to play.
 *
 * Each constant encodes a genuinely different game behaviour:
 *   FREE_FOR_ALL            — no restrictions whatsoever
 *   RED_COLOUR_ALTERNATING  — classic snooker Red→Colour sequence + strict clearance
 *   REDS_FIRST              — all Reds must be cleared before any Colour is allowed
 *   COLOURS_ONLY            — Reds are permanently forbidden; only Colours score
 *
 * The last two options represent brand-new validity semantics that do not exist in
 * Classic, Chaos, or Endurance mode — satisfying the requirement for true rule
 * customizability rather than mere parametric adjustment.
 */
enum ValidityPolicy {
    FREE_FOR_ALL(
        "Free for All",
        "Any card is always a legal play — total freedom, no ordering."),
    RED_COLOUR_ALTERNATING(
        "Red→Colour Sequence",
        "Classic snooker: a Red must precede every Colour. Strict clearance order enforced."),
    REDS_FIRST(
        "Reds Before Colours",
        "All Red cards must be cleared from the table before any Colour card is allowed."),
    COLOURS_ONLY(
        "Colours Only",
        "Red cards are permanently forbidden — only Colour and Action cards may be played.");

    final String displayName;
    final String description;

    ValidityPolicy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    boolean isValid(Card card, GameState state) throws InvalidMoveException {
        if (card instanceof ActionCard) return true;
        return switch (this) {
            case FREE_FOR_ALL -> true;
            case RED_COLOUR_ALTERNATING -> {
                if (state.isInClearanceMode()) {
                    if (card instanceof RedCard)
                        throw new InvalidMoveException("No reds remain — clearance phase is active!");
                    if (card instanceof ColourCard) {
                        int idx = state.getClearanceIndex();
                        if (idx >= SnookerRule.CLEARANCE_ORDER.length)
                            throw new InvalidMoveException("All colours potted — frame is over!");
                        if (!card.getName().equals(SnookerRule.CLEARANCE_ORDER[idx]))
                            throw new InvalidMoveException(
                                "Clearance order: expected " + SnookerRule.CLEARANCE_ORDER[idx]);
                        yield true;
                    }
                }
                if (card instanceof RedCard)    yield state.expectingRed();
                if (card instanceof ColourCard) yield state.lastPlayedRed();
                yield true;
            }
            case REDS_FIRST -> {
                // All reds must be played out before colours become available
                if (card instanceof ColourCard && state.getRedsOnTable() > 0) yield false;
                yield true;
            }
            case COLOURS_ONLY -> {
                if (card instanceof RedCard)
                    throw new InvalidMoveException("Colours Only mode: Red cards are not allowed!");
                yield true;
            }
        };
    }

    /** True when an invalid play of {@code card} should trigger a foul (not just a forfeiture). */
    boolean isFoulableInvalidPlay(Card card, GameState state) {
        return switch (this) {
            case RED_COLOUR_ALTERNATING ->
                card instanceof ColourCard && !state.lastPlayedRed() && !state.isInClearanceMode();
            case REDS_FIRST ->
                card instanceof ColourCard && state.getRedsOnTable() > 0;
            default -> false;
        };
    }
}

// =============================================================================

/**
 * ScoringPolicy — defines HOW points are calculated for a card play.
 *
 * FACE_VALUE   — standard: card's printed value (same as Classic)
 * MULTIPLIED   — face value × a configurable multiplier (parametric option kept for continuity)
 * FLAT_SCORE   — every card, Red or Colour, scores exactly 3 points (brand-new behaviour)
 * COLOUR_BONUS — Reds score 1 pt; Colours score 3× their face value (brand-new behaviour)
 *
 * FLAT_SCORE and COLOUR_BONUS create entirely different strategic incentives: in FLAT_SCORE
 * there is no reward for holding high-value Colours; in COLOUR_BONUS Reds become near-worthless
 * unless they unlock a Colour.
 */
enum ScoringPolicy {
    FACE_VALUE(
        "Face Value",
        "Standard: each card scores its printed point value."),
    MULTIPLIED(
        "Multiplied",
        "Face value × a custom multiplier you configure (0.5–3.0)."),
    FLAT_SCORE(
        "Flat Score",
        "Every card — Red or Colour — scores exactly 3 points regardless of type."),
    COLOUR_BONUS(
        "Colour Bonus",
        "Reds = 1 pt; Colour cards = 3× their face value. High-value Colours dominate.");

    final String displayName;
    final String description;

    ScoringPolicy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    int calculatePoints(Card card, GameState state, double multiplier) {
        return switch (this) {
            case FACE_VALUE   -> card.getPoints();
            case MULTIPLIED   -> (int) Math.round(card.getPoints() * multiplier);
            case FLAT_SCORE   -> (card instanceof RedCard || card instanceof ColourCard) ? 3 : card.getPoints();
            case COLOUR_BONUS -> {
                if (card instanceof RedCard)    yield 1;
                if (card instanceof ColourCard) yield card.getPoints() * 3;
                yield card.getPoints();
            }
        };
    }
}

// =============================================================================

/**
 * FoulBehavior — defines WHAT HAPPENS when a foul is committed.
 *
 * POINTS_PENALTY — standard snooker: foul points go to the opponent
 * DOUBLE_PENALTY — opponent receives twice the standard penalty
 * STAMINA_DRAIN  — instead of awarding points, the fouler loses 4 stamina (brand-new behaviour)
 *
 * STAMINA_DRAIN creates a completely new risk structure: fouls no longer help the opponent
 * at all, but they accelerate the fouler's own exhaustion — turning fouls into a
 * self-destructive mechanic rather than a gift to the other player.
 */
enum FoulBehavior {
    POINTS_PENALTY(
        "Points Penalty",
        "Standard: foul points are awarded to the opponent."),
    DOUBLE_PENALTY(
        "Double Penalty",
        "Opponent receives 2× the standard foul points — high-risk play."),
    STAMINA_DRAIN(
        "Stamina Drain",
        "No points awarded: the fouler loses 4 stamina instead. Fouls hurt you, not just help them.");

    final String displayName;
    final String description;

    FoulBehavior(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    void applyFoul(int standardPts, Card card, GameState state) {
        switch (this) {
            case POINTS_PENALTY -> state.setPendingFoul(standardPts, "illegal " + card.getName() + " play");
            case DOUBLE_PENALTY -> state.setPendingFoul(standardPts * 2, "double-penalty foul on " + card.getName());
            case STAMINA_DRAIN  -> state.setPendingStaminaPenalty(4);
        }
    }
}

// =============================================================================

/**
 * ComposableRule — a brand-new IGameRule assembled from three independent policy enums.
 *
 * Unlike the old parametric approach (which only tuned numbers), ComposableRule lets
 * the player select genuinely different behaviours in each dimension:
 *   • ValidityPolicy — which cards are legal (4 distinct logic paths)
 *   • ScoringPolicy  — how points are counted (4 distinct formulas)
 *   • FoulBehavior   — what a foul does (3 distinct effects)
 *
 * 4 × 4 × 3 = 48 possible rule combinations, each a unique game not achievable
 * by simply adjusting a slider. This satisfies the OCP: every existing rule class
 * is untouched; ComposableRule is a new class added to the hierarchy.
 * It also demonstrates LSP: any ComposableRule can replace any other IGameRule
 * inside GameController without any modification to the caller.
 */
class ComposableRule implements IGameRule {

    private final ValidityPolicy validityPolicy;
    private final ScoringPolicy  scoringPolicy;
    private final FoulBehavior   foulBehavior;
    private final double         scoreMultiplier;
    private final int            staminaDrainPerTurn;

    ComposableRule(ValidityPolicy vp, ScoringPolicy sp, FoulBehavior fb,
                   double multiplier, int staminaDrain) {
        this.validityPolicy      = vp;
        this.scoringPolicy       = sp;
        this.foulBehavior        = fb;
        this.scoreMultiplier     = multiplier;
        this.staminaDrainPerTurn = staminaDrain;
    }

    @Override
    public boolean isValid(Card card, GameState state) throws InvalidMoveException {
        return validityPolicy.isValid(card, state);
    }

    @Override
    public void onInvalidPlay(Card card, GameState state) {
        if (validityPolicy.isFoulableInvalidPlay(card, state)) {
            foulBehavior.applyFoul(calculateFoulPenalty(card, state), card, state);
        }
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        return scoringPolicy.calculatePoints(card, state, scoreMultiplier);
    }

    @Override public int getStaminaDrain() { return staminaDrainPerTurn; }

    // Read-back getters — used by SnookerGUI to pre-populate the config dialog
    public ValidityPolicy getValidityPolicy()    { return validityPolicy; }
    public ScoringPolicy  getScoringPolicy()     { return scoringPolicy; }
    public FoulBehavior   getFoulBehavior()      { return foulBehavior; }
    public double         getScoreMultiplier()   { return scoreMultiplier; }
    public int            getStaminaDrainValue() { return staminaDrainPerTurn; }
}

// =============================================================================

/**
 * ComposableRuleBuilder — assembles a ComposableRule policy by policy.
 *
 * Fluent API: each setter returns {@code this} so calls chain naturally.
 * Demonstrates: Builder pattern, method chaining, and ad-hoc polymorphism via
 * the overloaded RuleFactory.create(RuleType, ComposableRuleBuilder).
 *
 * The key difference from a purely parametric builder is that the "parameters"
 * here are behavioural strategies (enum constants), not numeric scalars.
 * Selecting REDS_FIRST instead of RED_COLOUR_ALTERNATING changes the entire
 * game structure, not just a difficulty dial.
 */
class ComposableRuleBuilder {

    private ValidityPolicy validityPolicy  = ValidityPolicy.RED_COLOUR_ALTERNATING;
    private ScoringPolicy  scoringPolicy   = ScoringPolicy.FACE_VALUE;
    private FoulBehavior   foulBehavior    = FoulBehavior.POINTS_PENALTY;
    private double         scoreMultiplier = 1.0;
    private int            staminaDrain    = 1;

    // Fluent policy setters
    public ComposableRuleBuilder validityPolicy(ValidityPolicy v) { validityPolicy = v; return this; }
    public ComposableRuleBuilder scoringPolicy(ScoringPolicy s)   { scoringPolicy  = s; return this; }
    public ComposableRuleBuilder foulBehavior(FoulBehavior f)     { foulBehavior   = f; return this; }
    public ComposableRuleBuilder scoreMultiplier(double v) { scoreMultiplier = Math.max(0.5, Math.min(3.0, v)); return this; }
    public ComposableRuleBuilder staminaDrain(int v)       { staminaDrain    = Math.max(1, Math.min(3, v));     return this; }

    // Getters — used by GUI to pre-populate the dialog on re-open
    public ValidityPolicy getValidityPolicy()  { return validityPolicy; }
    public ScoringPolicy  getScoringPolicy()   { return scoringPolicy; }
    public FoulBehavior   getFoulBehavior()    { return foulBehavior; }
    public double         getScoreMultiplier() { return scoreMultiplier; }
    public int            getStaminaDrain()    { return staminaDrain; }

    public ComposableRule build() {
        return new ComposableRule(validityPolicy, scoringPolicy, foulBehavior,
                                  scoreMultiplier, staminaDrain);
    }

    /**
     * Console-mode overload — prompts stdin for each policy selection.
     * Demonstrates overloading (same concept, different input source) and
     * the power of enum-based selection vs raw numbers.
     */
    public static ComposableRule buildFromConsole(java.util.Scanner sc) {
        ComposableRuleBuilder b = new ComposableRuleBuilder();
        System.out.println("\n=== Composable Rule Builder ===");

        System.out.println("Validity Policy: FREE_FOR_ALL / RED_COLOUR_ALTERNATING / REDS_FIRST / COLOURS_ONLY");
        System.out.print("Your choice: ");
        try { b.validityPolicy(ValidityPolicy.valueOf(sc.nextLine().trim().toUpperCase())); }
        catch (IllegalArgumentException ignored) { System.out.println("  (kept default)"); }

        System.out.println("Scoring Policy: FACE_VALUE / MULTIPLIED / FLAT_SCORE / COLOUR_BONUS");
        System.out.print("Your choice: ");
        try { b.scoringPolicy(ScoringPolicy.valueOf(sc.nextLine().trim().toUpperCase())); }
        catch (IllegalArgumentException ignored) { System.out.println("  (kept default)"); }

        if (b.scoringPolicy == ScoringPolicy.MULTIPLIED) {
            System.out.print("Score multiplier (0.5–3.0): ");
            try { b.scoreMultiplier(Double.parseDouble(sc.nextLine().trim())); }
            catch (NumberFormatException ignored) {}
        }

        System.out.println("Foul Behavior: POINTS_PENALTY / DOUBLE_PENALTY / STAMINA_DRAIN");
        System.out.print("Your choice: ");
        try { b.foulBehavior(FoulBehavior.valueOf(sc.nextLine().trim().toUpperCase())); }
        catch (IllegalArgumentException ignored) { System.out.println("  (kept default)"); }

        System.out.print("Stamina drain per turn (1–3): ");
        try { b.staminaDrain(Integer.parseInt(sc.nextLine().trim())); }
        catch (NumberFormatException ignored) {}

        return b.build();
    }
}