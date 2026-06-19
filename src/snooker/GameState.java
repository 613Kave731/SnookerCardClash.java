package snooker;

import java.util.HashMap;
import java.util.Map;

public class GameState {
    private final ScoreBoard scoreBoard;
    private final Deck<Card> deck;
    private final IGameRule  rule;          // injected rule — DIP: state carries the strategy
    private int currentBreak = 0;
    private boolean safetyActive = false;

    // Per-player sequence tracking: each player independently alternates Red→Colour→Red.
    // Keyed by player name; defaults to false (expecting Red) when absent.
    private final Map<String, Boolean> lastRedByPlayer = new HashMap<>();
    private String currentPlayerName = "";
    private boolean glueActive   = false;   // GlueCard effect


    // --- Snooker simulation state ---
    private int redsOnTable    = 15;   // decrements each time a RedCard is potted
    private int clearanceIndex = 0;    // index into CLEARANCE_ORDER (Yellow→Black)
    private int pendingFoulPoints = 0; // foul pts to award to opponent after the turn
    private boolean snookerTrapActive = false; // SnookerTrapCard effect

    public GameState(Deck<Card> deck, ScoreBoard sb, IGameRule rule) {
        this.deck  = deck;
        this.scoreBoard = sb;
        this.rule  = rule;
    }

    // --- Current player (set by GameController before each turn) ---
    public void setCurrentPlayer(String name) { currentPlayerName = name; }

    // --- Sequencing flags (per-player: each player tracks their own Red→Colour alternation) ---
    public boolean expectingRed()  { return !lastRedByPlayer.getOrDefault(currentPlayerName, false); }
    public boolean lastPlayedRed() { return  lastRedByPlayer.getOrDefault(currentPlayerName, false); }
    public void setLastPlayedRed(boolean v) { lastRedByPlayer.put(currentPlayerName, v); }

    // --- Safety (SafetyCard) ---
    public void applySafety()       { safetyActive = true; }
    public boolean isSafetyActive() { return safetyActive; }
    public void clearSafety()       { safetyActive = false; }

    // --- Glue (GlueCard) — blocks opponent's next high-value card ---
    public void applyGlue()        { glueActive = true; }
    public boolean isGlueActive()  { return glueActive; }
    public void clearGlue()        { glueActive = false; }

    // --- Snooker trap (SnookerTrapCard) — opponent must escape or foul ---
    public void applySnookerTrap()  { snookerTrapActive = true; }
    public boolean isSnookered()    { return snookerTrapActive; }
    public void clearSnookerTrap()  { snookerTrapActive = false; }

    // --- Break accounting ---
    public void addToBreak(int pts)    { currentBreak += pts; }
    public int  getCurrentBreak()      { return currentBreak; }
    public void commitBreak(String p)  {
        scoreBoard.addPoints(p, currentBreak);
        currentBreak = 0;
    }

    // --- Reds on table ---
    public int  getRedsOnTable()  { return redsOnTable; }
    public void decrementReds()   { if (redsOnTable > 0) redsOnTable--; }
    public boolean isInClearanceMode() { return redsOnTable == 0; }

    // --- Clearance phase tracking ---
    public int  getClearanceIndex()    { return clearanceIndex; }
    public void advanceClearanceIndex() { clearanceIndex++; }

    // --- Pending foul (set by rule, consumed by controller to award opponent pts) ---
    private String pendingFoulReason = "";
    public void setPendingFoul(int pts)                { setPendingFoul(pts, ""); }
    public void setPendingFoul(int pts, String reason) { pendingFoulPoints = pts; pendingFoulReason = reason; }
    public boolean hasPendingFoul()      { return pendingFoulPoints > 0; }
    public int    consumePendingFoul()   { int p = pendingFoulPoints; pendingFoulPoints = 0; return p; }
    public String consumePendingFoulReason() { String r = pendingFoulReason; pendingFoulReason = ""; return r; }

    // --- Pending stamina penalty (used by STAMINA_DRAIN foul behavior) ---
    private int pendingStaminaPenalty = 0;
    public void setPendingStaminaPenalty(int drain) { pendingStaminaPenalty = drain; }
    public boolean hasPendingStaminaPenalty()       { return pendingStaminaPenalty > 0; }
    public int consumePendingStaminaPenalty()       { int p = pendingStaminaPenalty; pendingStaminaPenalty = 0; return p; }

    // --- Accessors ---
    public IGameRule   getRule()       { return rule; }
    public Deck<Card>  getDeck()       { return deck; }
    public ScoreBoard  getScoreBoard() { return scoreBoard; }
}