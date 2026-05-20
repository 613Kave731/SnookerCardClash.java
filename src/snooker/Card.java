package snooker;

import java.util.*;

// ADT: Every card must be playable (Abstraction)
interface IPlayable {
    void play(GameState state) throws InvalidMoveException, FoulException;
    String getDescription();
}

// Information Hiding: Internal data is private[cite: 2]
public abstract class Card implements IPlayable {

    private final String name;
    private final int    points;

    protected Card(String name, int points) {
        this.name   = name;
        this.points = points;
    }

    public String getName()   { return name; }
    public int    getPoints() { return points; }

    @Override
    public String toString() { return name + "(" + points + "pts)"; }

    /**
     * Hook for cards that need to interact with the player's hand after being played.
     * Default is a no-op; MagnetCard overrides this to draw a Red from the deck.
     *
     * Keeping this separate from play() preserves the Single Responsibility Principle:
     * play() mutates game-state flags; applyHandEffect() mutates the player's hand.
     */
    void applyHandEffect(Hand hand, GameState state) { /* no-op by default */ }
}

// ─── Ball Cards ───────────────────────────────────────────────────────────────

// Inheritance: Specializing the Card blueprint[cite: 2]
abstract class BallCard extends Card {
    protected BallCard(String colour, int points) { super(colour, points); }
}

class RedCard extends BallCard {
    public RedCard() { super("Red", 1); }

    @Override
    public void play(GameState state) {
        state.setLastPlayedRed(true);
        state.decrementReds();   // red is potted permanently — never returns to deck
        System.out.println("  Potted: " + this + "  [Reds remaining: " + state.getRedsOnTable() + "]");
    }

    @Override
    public String getDescription() { return "Red ball – 1 point."; }
}

class ColourCard extends BallCard {
    public ColourCard(String colour, int points) { super(colour, points); }

    @Override
    public void play(GameState state) {
        state.setLastPlayedRed(false);
        System.out.println("  Potted: " + this);
        // In standard phase colours return to the spotted position (deck); in clearance they stay down.
        if (state.getRedsOnTable() > 0) {
            state.getDeck().addCard(this);
            System.out.println("  " + getName() + " respotted (returned to deck).");
        }
    }

    @Override
    public String getDescription() { return getName() + " ball – " + getPoints() + " points."; }
}

// ─── Action Cards ─────────────────────────────────────────────────────────────

abstract class ActionCard extends Card {
    protected ActionCard(String name) { super(name, 0); }
}

class SafetyCard extends ActionCard {
    public SafetyCard() { super("Safety"); }

    @Override
    public void play(GameState state) {
        state.applySafety();
        System.out.println("  SAFETY played — opponent loses next turn.");
    }

    @Override
    public String getDescription() { return "Safety shot — opponent skips turn."; }
}

class FoulCard extends ActionCard {
    public FoulCard() { super("Foul"); }

    @Override
    public void play(GameState state) throws FoulException {
        throw new FoulException("Foul committed!", 4);
    }

    @Override
    public String getDescription() { return "Foul — opponent gains 4 pts."; }
}

class FreeBallCard extends ActionCard {
    public FreeBallCard() { super("FreeBall"); }

    @Override
    public void play(GameState state) {
        state.setFreeBall(true);
        System.out.println("  FREE BALL — next ball treated as Red.");
    }

    @Override
    public String getDescription() { return "Free Ball — next ball counts as Red."; }
}

/**
 * MagnetCard — power-up that pulls a Red card directly from the deck into the hand.
 *
 * The actual draw uses applyHandEffect() rather than play(), because play() has no
 * access to the player's hand. This respects Information Hiding: the hand stays private
 * inside Player; MagnetCard receives it only for the duration of this call.
 */
class MagnetCard extends ActionCard {
    public MagnetCard() { super("Magnet"); }

    @Override
    public void play(GameState state) {
        System.out.println("  MAGNET activated — searching deck for a Red!");
    }

    @Override
    void applyHandEffect(Hand hand, GameState state) {
        // Typed local variable helps the compiler (and IDE) resolve the generic method call.
        Deck<Card> deck = state.getDeck();
        try {
            Card red = deck.drawIf(c -> c instanceof RedCard);
            hand.addCard(red);
            System.out.println("  Magnet: Red drawn from deck and added to your hand.");
        } catch (EmptyDeckException e) {
            System.out.println("  Magnet: No Reds remain in the deck.");
        }
    }

    @Override
    public String getDescription() { return "Draws a Red from the deck into your hand."; }
}

/**
 * GlueCard — power-up that sets a trap for the opponent.
 * Their next card play is intercepted: if it is high-value (≥ 5 pts) the card
 * is blocked and wasted; the glue is consumed regardless.
 */
class GlueCard extends ActionCard {
    public GlueCard() { super("Glue"); }

    @Override
    public void play(GameState state) {
        state.applyGlue();
        System.out.println("  GLUE set — opponent's next high-value card (≥5 pts) is blocked!");
    }

    @Override
    public String getDescription() { return "Blocks opponent's next high-value card (≥5 pts)."; }
}

// ─── Exceptions ───────────────────────────────────────────────────────────────

class InvalidMoveException extends Exception {
    public InvalidMoveException(String msg) { super(msg); }
}

class EmptyDeckException extends Exception {
    public EmptyDeckException() { super("Deck empty!"); }
}

class FoulException extends Exception {
    private final int penalty;
    public FoulException(String msg, int penalty) { super(msg); this.penalty = penalty; }
    public int getPenalty() { return penalty; }
}