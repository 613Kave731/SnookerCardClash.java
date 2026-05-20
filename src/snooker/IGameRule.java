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
}

// =============================================================================

/**
 * StandardRule — enforces traditional snooker ball sequencing.
 * A Red must precede every Colour; action cards are always legal.
 */
class StandardRule implements IGameRule {

    @Override
    public boolean isValid(Card card, GameState state) throws InvalidMoveException {
        if (card instanceof RedCard)    return state.expectingRed();
        if (card instanceof ColourCard) return state.lastPlayedRed();
        return true;    // action cards (Safety, FreeBall, Magnet, Glue) have no restriction
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        return card.getPoints();    // face value only, no bonuses
    }
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
            if (!state.lastPlayedRed()) {
                // Colour played when a Red was required — 7-point foul
                state.setPendingFoul(7);
                System.out.println("  FOUL! Colour played when Red was required — 7 pts awarded to opponent.");
                return false;
            }
            return true;
        }

        return true;
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

        if (card instanceof RedCard)    return state.expectingRed();
        if (card instanceof ColourCard) return state.lastPlayedRed();
        return true;
    }

    @Override
    public int calculatePoints(Card card, GameState state) {
        int base = card.getPoints();
        return (card instanceof ColourCard && base >= 5) ? base + 2 : base;
    }

    @Override
    public int getStaminaDrain() { return 2; }
}