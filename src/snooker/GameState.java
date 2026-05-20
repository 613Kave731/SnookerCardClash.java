package snooker;

public class GameState {
    private final ScoreBoard scoreBoard;
    private final Deck<Card> deck;
    private final IGameRule  rule;          // injected rule — DIP: state carries the strategy
    private int currentBreak = 0;
    private boolean lastWasRed = false, freeBall = false;
    private boolean safetyActive = false;
    private boolean glueActive   = false;   // GlueCard effect


    // --- Snooker simulation state ---
    private int redsOnTable    = 10;   // decrements each time a RedCard is potted
    private int clearanceIndex = 0;    // index into CLEARANCE_ORDER (Yellow→Black)
    private int pendingFoulPoints = 0; // foul pts to award to opponent after the turn

    public GameState(Deck<Card> deck, ScoreBoard sb, IGameRule rule) {
        this.deck  = deck;
        this.scoreBoard = sb;
        this.rule  = rule;
    }

    // --- Sequencing flags (read by IGameRule implementations) ---
    public boolean expectingRed()  { return !lastWasRed || freeBall; }
    public boolean lastPlayedRed() { return lastWasRed  || freeBall; }
    public void setLastPlayedRed(boolean v) { lastWasRed = v; freeBall = false; }
    public void setFreeBall(boolean v)      { freeBall = v; }

    // --- Safety (SafetyCard) ---
    public void applySafety()       { safetyActive = true; }
    public boolean isSafetyActive() { return safetyActive; }
    public void clearSafety()       { safetyActive = false; }

    // --- Glue (GlueCard) — blocks opponent's next high-value card ---
    public void applyGlue()        { glueActive = true; }
    public boolean isGlueActive()  { return glueActive; }
    public void clearGlue()        { glueActive = false; }

    // --- Break accounting ---
    public void addToBreak(int pts)    { currentBreak += pts; }
    public int  getCurrentBreak()      { return currentBreak; }
    public void commitBreak(String p)  {
        scoreBoard.addPoints(p, currentBreak);
        currentBreak = 0;
        lastWasRed   = false;
    }

    // --- Reds on table ---
    public int  getRedsOnTable()  { return redsOnTable; }
    public void decrementReds()   { if (redsOnTable > 0) redsOnTable--; }
    public boolean isInClearanceMode() { return redsOnTable == 0; }

    // --- Clearance phase tracking ---
    public int  getClearanceIndex()    { return clearanceIndex; }
    public void advanceClearanceIndex() { clearanceIndex++; }

    // --- Pending foul (set by rule, consumed by controller to award opponent pts) ---
    public void setPendingFoul(int pts)  { pendingFoulPoints = pts; }
    public boolean hasPendingFoul()      { return pendingFoulPoints > 0; }
    public int consumePendingFoul()      { int p = pendingFoulPoints; pendingFoulPoints = 0; return p; }

    // --- Accessors ---
    public IGameRule   getRule()       { return rule; }
    public Deck<Card>  getDeck()       { return deck; }
    public ScoreBoard  getScoreBoard() { return scoreBoard; }
}