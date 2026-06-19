package snooker;

import java.util.*;

// Interface for Multityping/Subtyping
interface IScoreable {
    int getScore();
    void addScore(int points);
}

/**
 * Player — abstract base class for all player types.
 *
 * Encapsulation: stamina and hand are private; subclasses access them
 * only through the protected/public API defined here.
 *
 * Template Method Pattern: chooseCard() is abstract, forcing each concrete
 * subclass to provide its own decision strategy (HumanPlayer, AggressiveAI, GUIPlayer).
 */
public abstract class Player implements IScoreable {

    private final String name;
    private final Hand   hand     = new Hand();  // Composition
    private       int    stamina  = 20;           // Stamina pool (max 20)
    private       boolean exhausted = false;      // True once stamina reaches zero

    protected Player(String name) { this.name = name; }

    // --- Identity & accessors ------------------------------------------------

    public String  getName()      { return name; }
    public Hand    getHand()      { return hand; }
    public int     getStamina()   { return stamina; }
    public boolean isExhausted()  { return exhausted; }

    /** Restores up to {@code amount} stamina, capped at 20. Used by stamina challenges. */
    public void restoreStamina(int amount) {
        stamina = Math.min(20, stamina + amount);
    }

    /** Applies a foul-based stamina penalty (e.g. STAMINA_DRAIN foul behavior). */
    public void loseStamina(int drain) { reduceStamina(drain); }

    /** Resets stamina and clears hand for the start of a new frame in a best-of match. */
    public void resetForNewFrame() {
        stamina   = 20;
        exhausted = false;
        hand.clear();
    }

    /**
     * Reduces stamina by {@code drain} (rule-dependent) each card play.
     * Sets exhausted=true when stamina first reaches zero — GameController reads this
     * to trigger game-over, so no recovery is applied here.
     */
    private void reduceStamina(int drain) {
        stamina = Math.max(0, stamina - drain);
        if (stamina == 0) exhausted = true;
    }

    @Override public int  getScore()           { return 0; }  // Managed by ScoreBoard
    @Override public void addScore(int points) {}

    public void drawCard(Deck<Card> deck) throws EmptyDeckException {
        hand.addCard(deck.draw());
    }

    // --- Template Method: subclasses implement their own decision logic -------

    public abstract Card chooseCard(GameState state);

    // --- Turn execution -------------------------------------------------------

    /**
     * Executes one complete turn for this player.
     *
     * The method follows the Tell-Don't-Ask principle: it asks the injected
     * IGameRule (via GameState) whether the move is valid and how many points
     * it earns, then tells the card to execute its effects. The player never
     * peeks inside the rule's implementation.
     *
     * Order matters: isValid() and calculatePoints() are called BEFORE
     * card.play() so they can read the correct pre-play state flags.
     */
    public void playTurn(GameState state) {

        IGameRule rule = state.getRule();  // resolved early — needed by snooker + glue + stamina steps

        // 1. Safety block (from SafetyCard)
        if (state.isSafetyActive()) {
            System.out.println(name + " blocked by Safety — turn skipped.");
            state.clearSafety();
            return;
        }

        // 1.5. Snooker block (from SnookerTrapCard) — auto-escape or auto-foul
        if (state.isSnookered()) {
            state.clearSnookerTrap();
            List<Card> handCards = hand.getCards();
            int escIdx = -1;
            for (int i = 0; i < handCards.size(); i++) {
                if (handCards.get(i) instanceof EscapeCard) { escIdx = i; break; }
            }
            if (escIdx >= 0) {
                hand.removeCard(escIdx);
                System.out.println(name + " plays EscapeCard — snooker evaded! No foul.");
            } else {
                state.setPendingFoul(4, "snookered with no escape card");
                System.out.println(name + " has no EscapeCard — FOUL! 4 pts to opponent.");
            }
            reduceStamina(rule.getStaminaDrain());
            System.out.println("  [Stamina: " + name + " → " + stamina + "/20]");
            return;
        }

        // 2. Stamina depleted — game-over condition; GameController detects this via isExhausted()
        if (getStamina() <= 0) {
            System.out.println(name + " has no stamina left — exhaustion game-over!");
            return;
        }

        // 3. Empty hand — deck may be exhausted; turn is skipped
        if (getHand().getCards().isEmpty()) {
            System.out.println(name + " has no cards — turn skipped.");
            return;
        }

        Card chosen = chooseCard(state);

        // 4. Validate via the injected rule (DIP: this method is rule-agnostic)
        //    InvalidMoveException signals a strict ordering violation (e.g. clearance phase).
        boolean valid;
        try {
            valid = rule.isValid(chosen, state);
        } catch (InvalidMoveException e) {
            System.out.println("  ILLEGAL MOVE [" + chosen.getName() + "]: " + e.getMessage()
                + " — turn forfeited.");
            hand.removeCard(hand.getCards().indexOf(chosen));
            reduceStamina(rule.getStaminaDrain());
            return;
        }

        if (!valid) {
            rule.onInvalidPlay(chosen, state);   // apply foul penalty only when card actually played
            System.out.println("  Invalid move [" + chosen.getName()
                + "] under current rules — turn forfeited.");
            hand.removeCard(hand.getCards().indexOf(chosen));
            reduceStamina(rule.getStaminaDrain());
            return;
        }

        // 4.5. Glue block — checked after validation so invalid moves don't consume Glue.
        //      Glue is consumed on any valid play; only high-value (≥5 pt) cards are blocked.
        if (state.isGlueActive()) {
            state.clearGlue();
            if (chosen.getPoints() >= 5) {
                System.out.println("  GLUE blocks " + name + "'s "
                    + chosen.getName() + "! Card wasted.");
                hand.removeCard(hand.getCards().indexOf(chosen));
                reduceStamina(rule.getStaminaDrain());
                return;
            }
        }

        // 6. Calculate points BEFORE play() mutates state flags
        int points = rule.calculatePoints(chosen, state);
        hand.removeCard(hand.getCards().indexOf(chosen));

        // 7. Execute the card — state mutations (flags) happen here
        try {
            chosen.play(state);
            state.addToBreak(points);             // rule-adjusted points
            chosen.applyHandEffect(hand, state);  // MagnetCard: draws a Red
            // Advance clearance index after a valid colour pot in clearance phase
            if (state.isInClearanceMode() && chosen instanceof ColourCard) {
                state.advanceClearanceIndex();
            }
        } catch (FoulException e) {
            System.out.println("  FOUL by " + name + ": " + e.getMessage()
                + "  (" + e.getPenalty() + " pts penalty — not yet applied to opponent)");
        } catch (Exception e) {
            System.out.println("  Issue during " + name + "'s turn: " + e.getMessage());
        }

        reduceStamina(rule.getStaminaDrain());
        System.out.println("  [Stamina: " + name + " → " + stamina + "/20]");
    }
}

// =============================================================================

/**
 * HumanPlayer — console-based player that reads moves from Scanner.
 *
 * Stamina Challenge: when stamina falls below 5, the player is offered
 * a random multiplication question before choosing a card. A correct
 * answer restores 5 stamina, rewarding engagement under fatigue.
 */
class HumanPlayer extends Player {

    private final Scanner scanner = new Scanner(System.in);
    private final Random  random  = new Random();

    public HumanPlayer(String name) { super(name); }

    @Override
    public Card chooseCard(GameState state) {
        if (getStamina() < 5) {
            staminaChallenge();
        }

        List<Card> currentHand = getHand().getCards();

        System.out.println("\n========================================");
        System.out.println("  YOUR TURN: " + getName().toUpperCase());
        System.out.println("  BREAK: " + state.getCurrentBreak()
            + "  |  STAMINA: " + getStamina() + "/20");
        System.out.println("========================================");

        for (int i = 0; i < currentHand.size(); i++) {
            System.out.println("[" + i + "] " + currentHand.get(i).getDescription());
        }

        int choice = -1;
        while (choice < 0 || choice >= currentHand.size()) {
            System.out.print("\nEnter index (0-" + (currentHand.size() - 1) + "): ");
            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();
            } else {
                System.out.println("Please enter a number.");
                scanner.next();
            }
        }
        return currentHand.get(choice);
    }

    /**
     * Presents a random multiplication question via Scanner.
     * A correct answer restores 5 stamina (capped at 20).
     * Encapsulation: this is private — no external code can trigger it directly;
     * it is an internal recovery mechanism owned by HumanPlayer.
     */
    private void staminaChallenge() {
        int a = random.nextInt(10) + 1;
        int b = random.nextInt(10) + 1;
        System.out.println("\n  *** STAMINA CHALLENGE *** (Stamina = " + getStamina() + "/20)");
        System.out.println("  Quick! What is " + a + " × " + b + "?");
        System.out.print("  Your answer: ");

        if (scanner.hasNextInt() && scanner.nextInt() == a * b) {
            restoreStamina(5);
            System.out.println("  Correct! +5 stamina restored. Now at " + getStamina() + "/20.");
        } else {
            System.out.println("  Wrong! No stamina restored. Stay focused!");
            scanner.nextLine();  // clear any remaining input
        }
    }
}

// =============================================================================

/**
 * AggressiveAI — rule-aware AI that always picks the highest-scoring valid card.
 *
 * Uses IGameRule (via GameState) to filter legal moves before scoring them,
 * demonstrating that even the AI depends only on the rule abstraction (DIP).
 */
class AggressiveAI extends Player {

    public AggressiveAI(String name) { super(name); }

    @Override
    public Card chooseCard(GameState state) {
        IGameRule rule = state.getRule();
        List<Card> hand = getHand().getCards();

        // Highest-value valid card; if none found, fall back to an ActionCard (always legal)
        // before giving up with hand.get(0). This avoids spurious fouls from invalid ball plays.
        return hand.stream()
            .filter(c -> { try { return rule.isValid(c, state); } catch (InvalidMoveException e) { return false; } })
            .max(Comparator.comparingInt(c -> rule.calculatePoints(c, state)))
            .orElseGet(() -> hand.stream()
                .filter(c -> c instanceof ActionCard)
                .findFirst()
                .orElse(hand.get(0)));
    }
}