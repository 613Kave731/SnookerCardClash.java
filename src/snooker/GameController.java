package snooker;

import java.util.*;

/**
 * GameController manages the high-level game loop.
 * It depends on abstractions (Player, Card) rather than specifics.
 */
public class GameController {
    private final Player[] players;
    private final GameState state;
    private int turnIndex = 0;

    /**
     * Dependency Inversion Principle: GameController depends on the IGameRule
     * abstraction, not on StandardRule or ChaosModeRule. The caller decides
     * which rules govern the session by injecting the appropriate implementation.
     */
    public GameController(Player p1, Player p2, IGameRule rule) {
        this.players = new Player[]{p1, p2};

        ScoreBoard sb = new ScoreBoard();
        sb.register(p1.getName());
        sb.register(p2.getName());

        this.state = new GameState(CardFactory.buildStandardDeck(), sb, rule);
    }

    // --- Accessors for GUI (Dependency Injection: View reads the Model via Controller) ---

    /** Exposes the Model so it can be injected into the View. */
    public GameState getState() { return state; }

    /** Returns the player whose turn it currently is. */
    public Player getCurrentPlayer() { return players[turnIndex % 2]; }

    /**
     * Game ends when the Black ball is potted in clearance (all 6 colours cleared),
     * when any player's stamina hits zero, or as a failsafe when deck and hands are empty.
     */
    public boolean isGameOver() {
        if (state.getClearanceIndex() >= 6) return true;
        for (Player p : players) {
            if (p.isExhausted()) return true;
        }
        // Failsafe: nowhere left to draw or play
        if (state.getDeck().size() == 0) {
            boolean allHandsEmpty = true;
            for (Player p : players) {
                if (!p.getHand().getCards().isEmpty()) { allHandsEmpty = false; break; }
            }
            if (allHandsEmpty) return true;
        }
        return false;
    }

    /** Returns the name of the winning player, or "Nobody (Draw!)" for a tie. */
    public String getWinnerName() {
        // Exhausted player loses — opponent wins
        for (Player p : players) {
            if (p.isExhausted()) {
                for (Player other : players) {
                    if (other != p) return other.getName();
                }
            }
        }
        // Normal end: highest score wins
        ScoreBoard sb = state.getScoreBoard();
        int s0 = sb.getScore(players[0].getName());
        int s1 = sb.getScore(players[1].getName());
        if (s0 > s1) return players[0].getName();
        if (s1 > s0) return players[1].getName();
        return "Nobody (Draw!)";
    }

    /** Human-readable description of why the game ended. */
    public String getEndReason() {
        if (state.getClearanceIndex() >= 6) return "Black ball potted — full clearance complete!";
        for (Player p : players) {
            if (p.isExhausted()) return p.getName() + " ran out of stamina!";
        }
        return "Deck exhausted — final scores stand.";
    }

    // --- GUI-mode lifecycle ---

    /** Public initialisation step: deals initial hands without starting the game loop.
     *  Called by the GUI launcher before the window is shown. */
    public void initialise() { dealInitialHands(); }

    /** Plays a single turn (GUI mode). The View calls this after the user picks a card.
     *  Follows Tell, Don't Ask: the caller simply tells the controller to advance. */
    public void playNextTurn() {
        if (isGameOver()) return;
        Player current = players[turnIndex % 2];
        current.playTurn(state);
        // Award any pending foul (e.g. colour-when-red-required) to the opponent
        if (state.hasPendingFoul()) {
            Player opponent = players[(turnIndex + 1) % 2];
            int foulPts = state.consumePendingFoul();
            String reason = state.consumePendingFoulReason();
            state.getScoreBoard().addPoints(opponent.getName(), foulPts);
            String msg = "  Foul! " + foulPts + " points awarded to " + opponent.getName();
            if (!reason.isEmpty()) msg += " for " + reason;
            System.out.println(msg + ".");
        }
        state.commitBreak(current.getName());
        state.getScoreBoard().print();
        try {
            current.drawCard(state.getDeck());
        } catch (EmptyDeckException e) {
            System.out.println("Deck exhausted!");
        }
        turnIndex++;
    }

    // --- Console-mode entry point (unchanged) ---

    public void start() {
        System.out.println("Dealing cards to players...");
        dealInitialHands();
        runGameLoop();
    }

    private void dealInitialHands() {
        for (int i = 0; i < 5; i++) {
            for (Player p : players) {
                try {
                    p.drawCard(state.getDeck());
                } catch (Exception e) {
                    System.err.println("Error during initial deal: " + e.getMessage());
                }
            }
        }
    }

    private void runGameLoop() {
        int turn = 0;
        while (!isGameOver()) {
            Player current = players[turn % 2];
            current.playTurn(state);

            if (state.hasPendingFoul()) {
                Player opponent = players[(turn + 1) % 2];
                int foulPts = state.consumePendingFoul();
                String reason = state.consumePendingFoulReason();
                state.getScoreBoard().addPoints(opponent.getName(), foulPts);
                String msg = "  Foul! " + foulPts + " points awarded to " + opponent.getName();
                if (!reason.isEmpty()) msg += " for " + reason;
                System.out.println(msg + ".");
            }

            state.commitBreak(current.getName());
            state.getScoreBoard().print();

            try {
                current.drawCard(state.getDeck());
            } catch (EmptyDeckException e) {
                System.out.println("Deck exhausted!");
            }
            turn++;
        }
        System.out.println("\n=== GAME OVER: " + getEndReason() + " ===");
        System.out.println("Winner: " + getWinnerName());
        state.getScoreBoard().print();
    }
}

/**
 * CardFactory handles the complexity of building the deck.
 * This applies the Open/Closed Principle (OCP).
 */
class CardFactory {
    public static Deck<Card> buildStandardDeck() {
        Deck<Card> deck = new Deck<>();

        // Add 10 Reds (matches GameState.redsOnTable initial value)
        for (int i = 0; i < 10; i++) {
            deck.addCard(new RedCard());
        }

        // Add one of each colour ball
        deck.addCard(new ColourCard("Yellow", 2));
        deck.addCard(new ColourCard("Green", 3));
        deck.addCard(new ColourCard("Brown", 4));
        deck.addCard(new ColourCard("Blue", 5));
        deck.addCard(new ColourCard("Pink", 6));
        deck.addCard(new ColourCard("Black", 7));

        // Tactical action cards
        deck.addCard(new SafetyCard());
        deck.addCard(new FreeBallCard());

        // Power-up cards
        deck.addCard(new MagnetCard());
        deck.addCard(new MagnetCard());
        deck.addCard(new GlueCard());
        deck.addCard(new GlueCard());

        deck.shuffle();
        return deck;
    }
}