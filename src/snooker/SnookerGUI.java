package snooker;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Random;

/**
 * SnookerGUI — the VIEW in the MVC pattern.
 *
 * Responsibilities:
 *   - Show the Rule Selection screen before the match begins
 *   - Display the current game state (hand, scores, break counter, active rule)
 *   - Translate user button-clicks into controller commands
 *   - Refresh itself every time the model changes
 *
 * All dependencies (players, then controller + model) are injected;
 * this class never constructs game-logic objects, enforcing Loose Coupling.
 * CardLayout drives the screen transition: ruleSelection → game.
 */
public class SnookerGUI extends JFrame {

    // === Player dependencies — fixed at construction ===
    private final GUIPlayer humanPlayer;
    private final Player    aiPlayer;

    // === Game dependencies — injected after rule selection ===
    private GameController       controller;
    private GameState            state;
    private RuleFactory.RuleType activeRuleType;

    // === CardLayout for screen transitions ===
    private final CardLayout cardLayout;
    private final JPanel     mainPanel;

    // Default rule highlighted on the selection screen
    private RuleFactory.RuleType pendingRuleType = RuleFactory.RuleType.CLASSIC;

    // === Best-of match tracking ===
    private int[] framesWon        = {0, 0};  // [0] = human, [1] = AI
    private int   bestOf            = 3;       // configurable on the selection screen
    private boolean frameResultRecorded = false;
    private JPanel  currentGamePanel;

    // === Game UI components (created lazily inside buildGamePanel) ===
    private JLabel    ruleLabel;
    private JLabel    breakLabel;
    private JLabel    statusLabel;
    private JPanel    handPanel;
    private JTextArea logArea;
    private JPanel    scorePanel;
    private JPanel    opponentPanel;

    /**
     * Constructor Injection: receives only the player objects.
     * The controller and rule are created after the player selects a rule set.
     */
    public SnookerGUI(GUIPlayer humanPlayer, Player aiPlayer) {
        this.humanPlayer = humanPlayer;
        this.aiPlayer    = aiPlayer;

        setTitle("Snooker Card Clash");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 660);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel  = new JPanel(cardLayout);
        setContentPane(mainPanel);

        mainPanel.add(buildRuleSelectionPanel(), "ruleSelection");
        // "game" panel is added lazily in startGame() after rule is chosen
    }

    // =========================================================================
    // Rule Selection Screen
    // =========================================================================

    private JPanel buildRuleSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(new Color(0, 60, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        JPanel southStack = new JPanel(new GridLayout(2, 1, 0, 12));
        southStack.setBackground(new Color(0, 60, 0));
        southStack.add(buildMatchFormatPanel());
        southStack.add(buildPlayButtonPanel());

        panel.add(buildSelectionTitle(), BorderLayout.NORTH);
        panel.add(buildRuleButtons(),    BorderLayout.CENTER);
        panel.add(southStack,            BorderLayout.SOUTH);
        return panel;
    }

    /** Toggle buttons for "1 Frame", "Best of 3", "Best of 5". */
    private JPanel buildMatchFormatPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        panel.setBackground(new Color(0, 60, 0));

        JLabel lbl = new JLabel("Match Format:", SwingConstants.CENTER);
        lbl.setForeground(new Color(200, 200, 200));
        lbl.setFont(new Font("Arial", Font.PLAIN, 13));
        panel.add(lbl);

        String[] labels = {"1 Frame", "Best of 3", "Best of 5"};
        int[]    values = {1, 3, 5};
        JToggleButton[] btns  = new JToggleButton[3];
        ButtonGroup     group = new ButtonGroup();
        Color gold = new Color(212, 175, 55);
        Color dim  = new Color(80, 80, 80);

        for (int i = 0; i < 3; i++) {
            final int val = values[i];
            JToggleButton btn = new JToggleButton(labels[i]);
            btn.setFont(new Font("Arial", Font.PLAIN, 12));
            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(44, 44, 44));
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setFocusPainted(false);
            btn.setSelected(val == bestOf);
            btn.setBorder(val == bestOf
                ? BorderFactory.createLineBorder(gold, 2)
                : BorderFactory.createLineBorder(dim,  1));
            btns[i] = btn;
            group.add(btn);
            btn.addActionListener(e -> {
                bestOf = val;
                for (JToggleButton b : btns) b.setBorder(BorderFactory.createLineBorder(dim, 1));
                btn.setBorder(BorderFactory.createLineBorder(gold, 2));
            });
            panel.add(btn);
        }
        return panel;
    }

    private JPanel buildSelectionTitle() {
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 8));
        titlePanel.setBackground(new Color(0, 60, 0));
        titlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JLabel title = new JLabel("SNOOKER CARD CLASH", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Select a Rule Set to Begin", SwingConstants.CENTER);
        subtitle.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitle.setForeground(new Color(200, 200, 200));

        titlePanel.add(title);
        titlePanel.add(subtitle);
        return titlePanel;
    }

    private JPanel buildRuleButtons() {
        JPanel rulesPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        rulesPanel.setBackground(new Color(0, 60, 0));

        Color charcoal = new Color(44, 44, 44);       // #2C2C2C
        Color gold     = new Color(212, 175, 55);      // gold highlight for selected card
        Color dimBorder = new Color(80, 80, 80);

        RuleFactory.RuleType[] types   = RuleFactory.RuleType.values();
        JToggleButton[]        buttons = new JToggleButton[types.length];
        ButtonGroup            group   = new ButtonGroup();

        for (int i = 0; i < types.length; i++) {
            RuleFactory.RuleType type = types[i];
            JToggleButton btn = new JToggleButton(
                "<html><center><b>" + type.getDisplayName() + "</b><br/><br/>" +
                "<small><i>" + type.getDescription() + "</i></small></center></html>");
            btn.setFont(new Font("Arial", Font.PLAIN, 12));
            btn.setForeground(Color.WHITE);     // #FFFFFF — always white text
            btn.setFocusPainted(false);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setContentAreaFilled(true);
            btn.setPreferredSize(new Dimension(200, 130));
            btn.setBackground(charcoal);        // #2C2C2C — dark charcoal background

            boolean isDefault = (type == RuleFactory.RuleType.CLASSIC);
            btn.setSelected(isDefault);
            btn.setBorder(isDefault
                ? BorderFactory.createLineBorder(gold, 3)
                : BorderFactory.createLineBorder(dimBorder, 1));

            buttons[i] = btn;
            group.add(btn);

            btn.addActionListener(e -> {
                pendingRuleType = type;
                for (JToggleButton b : buttons) {
                    b.setBackground(charcoal);
                    b.setBorder(BorderFactory.createLineBorder(dimBorder, 1));
                }
                btn.setBackground(charcoal);
                btn.setBorder(BorderFactory.createLineBorder(gold, 3));
            });

            rulesPanel.add(btn);
        }
        return rulesPanel;
    }

    private JPanel buildPlayButtonPanel() {
        JButton playBtn = new JButton("  PLAY");
        playBtn.setBackground(new Color(200, 150, 0));
        playBtn.setForeground(Color.WHITE);
        playBtn.setFont(new Font("Arial", Font.BOLD, 18));
        playBtn.setPreferredSize(new Dimension(160, 50));
        playBtn.setFocusPainted(false);
        playBtn.setOpaque(true);
        playBtn.setBorderPainted(false);
        playBtn.addActionListener(e -> startGame(pendingRuleType));

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        south.setBackground(new Color(0, 60, 0));
        south.add(playBtn);
        return south;
    }

    // =========================================================================
    // Game start — dependency injection point for controller + rule
    // =========================================================================

    /**
     * Creates the GameController with the user-selected rule (Dependency Injection),
     * builds the game UI, and transitions to it via CardLayout.
     */
    private void startGame(RuleFactory.RuleType ruleType) {
        activeRuleType = ruleType;
        controller     = new GameController(humanPlayer, aiPlayer, RuleFactory.create(ruleType));
        state          = controller.getState();
        controller.initialise();
        frameResultRecorded = false;

        if (currentGamePanel != null) mainPanel.remove(currentGamePanel);
        currentGamePanel = buildGamePanel();
        mainPanel.add(currentGamePanel, "game");
        redirectSystemOut();
        cardLayout.show(mainPanel, "game");
        refreshDisplay();
        maybeScheduleAITurn();
    }

    /** Resets players and starts the next frame without changing the rule or match format. */
    private void startNextFrame() {
        humanPlayer.resetForNewFrame();
        aiPlayer.resetForNewFrame();
        startGame(activeRuleType);
    }

    // =========================================================================
    // Game Screen Construction
    // =========================================================================

    private JPanel buildGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(0, 80, 0));

        panel.add(buildTopPanel(),   BorderLayout.NORTH);
        panel.add(buildHandPanel(),  BorderLayout.CENTER);
        panel.add(buildScorePanel(), BorderLayout.EAST);
        panel.add(buildLogPanel(),   BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTopPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(new Color(0, 80, 0));
        wrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        // Opponent panel — card backs only, no card names (encapsulation)
        opponentPanel = new JPanel(new BorderLayout(5, 2));
        opponentPanel.setBackground(new Color(0, 55, 0));
        opponentPanel.setPreferredSize(new Dimension(0, 80));
        TitledBorder oppBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(140, 140, 140), 1),
                "Opponent: " + aiPlayer.getName());
        oppBorder.setTitleColor(new Color(200, 200, 200));
        opponentPanel.setBorder(oppBorder);
        wrapper.add(opponentPanel, BorderLayout.NORTH);

        // Three-row info panel: rule label + break counter + status message
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 0, 2));
        infoPanel.setBackground(new Color(0, 80, 0));

        ruleLabel = new JLabel("Rule Set: " + activeRuleType.getDisplayName(), SwingConstants.CENTER);
        ruleLabel.setFont(new Font("Arial", Font.BOLD, 13));
        ruleLabel.setForeground(new Color(255, 215, 0));   // gold — visible at a glance

        breakLabel = new JLabel("Current Break: 0", SwingConstants.CENTER);
        breakLabel.setFont(new Font("Arial", Font.BOLD, 22));
        breakLabel.setForeground(Color.WHITE);

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        statusLabel.setForeground(Color.YELLOW);

        infoPanel.add(ruleLabel);
        infoPanel.add(breakLabel);
        infoPanel.add(statusLabel);
        wrapper.add(infoPanel, BorderLayout.CENTER);

        return wrapper;
    }

    private JPanel buildHandPanel() {
        handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        handPanel.setBackground(new Color(0, 100, 0));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 1),
                "Your Hand — click a card to play it");
        border.setTitleColor(Color.WHITE);
        handPanel.setBorder(border);
        return handPanel;
    }

    private JPanel buildScorePanel() {
        scorePanel = new JPanel(new GridLayout(0, 1, 5, 5));
        scorePanel.setBackground(new Color(0, 60, 0));
        scorePanel.setPreferredSize(new Dimension(185, 0));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 1), "Scores");
        border.setTitleColor(Color.WHITE);
        scorePanel.setBorder(border);
        return scorePanel;
    }

    private JScrollPane buildLogPanel() {
        logArea = new JTextArea(6, 40);
        logArea.setEditable(false);
        logArea.setBackground(new Color(15, 15, 15));
        logArea.setForeground(new Color(160, 255, 130));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(0, 130));
        return scroll;
    }

    // =========================================================================
    // System.out redirect — game-engine messages appear in the log panel
    // =========================================================================

    private void redirectSystemOut() {
        PrintStream guiStream = new PrintStream(new OutputStream() {
            private final StringBuilder buf = new StringBuilder();
            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = buf.toString();
                    buf.setLength(0);
                    SwingUtilities.invokeLater(() -> appendLog(line));
                } else {
                    buf.append((char) b);
                }
            }
        });
        System.setOut(guiStream);
    }

    // =========================================================================
    // Display refresh — pure read of the Model, no game-logic side-effects
    // =========================================================================

    /**
     * Rebuilds every UI component to match the current Model state.
     * Called after any controller action so the View stays in sync.
     */
    private void refreshDisplay() {
        String phase = state.isInClearanceMode() ? "PHASE: CLEARANCE" : "PHASE: STANDARD";
        breakLabel.setText("Break: " + state.getCurrentBreak()
            + "   |   Reds: " + state.getRedsOnTable()
            + "   |   " + phase);
        refreshScorePanel();
        refreshOpponentPanel();

        if (controller.isGameOver()) {
            renderGameOver();
            return;
        }

        boolean isHumanTurn = (controller.getCurrentPlayer() == humanPlayer);

        if (isHumanTurn && humanPlayer.getStamina() <= 0) {
            renderStaminaRest();
        } else if (isHumanTurn && state.isSafetyActive()) {
            renderSafetyBlock();
        } else if (isHumanTurn && state.isSnookered()) {
            renderSnookered();
        } else if (isHumanTurn) {
            renderHumanHand();
        } else {
            renderAIWaiting();
        }
    }

    // --- Sub-renderers (each one owns exactly one UI state) ------------------

    private void renderHumanHand() {
        statusLabel.setText("YOUR TURN: " + humanPlayer.getName().toUpperCase()
            + "  |  Stamina: " + humanPlayer.getStamina() + "/20");
        statusLabel.setForeground(humanPlayer.getStamina() < 5 ? Color.ORANGE : Color.YELLOW);

        handPanel.removeAll();
        List<Card> cards = humanPlayer.getHand().getCards();
        for (Card card : cards) {
            JButton btn = makeCardButton(card);
            // Tell, Don't Ask: clicking TELLS the controller which card to play.
            // The View does not check game rules; it delegates to the Controller.
            btn.addActionListener(e -> onCardPlayed(card));
            handPanel.add(btn);
        }

        // Stamina Challenge button — available when stamina is critically low
        if (humanPlayer.getStamina() < 5) {
            JButton challengeBtn = new JButton(
                "<html><center>STAMINA CHALLENGE<br/><small>Answer to restore +5</small></center></html>");
            challengeBtn.setBackground(new Color(160, 100, 0));
            challengeBtn.setForeground(Color.WHITE);
            challengeBtn.setPreferredSize(new Dimension(150, 60));
            challengeBtn.setOpaque(true);
            challengeBtn.setBorderPainted(false);
            challengeBtn.setFocusPainted(false);
            challengeBtn.addActionListener(e -> triggerGUIStaminaChallenge());
            handPanel.add(challengeBtn);
        }

        handPanel.revalidate();
        handPanel.repaint();
    }

    private void renderAIWaiting() {
        statusLabel.setText(aiPlayer.getName() + " is thinking...");
        statusLabel.setForeground(Color.CYAN);

        handPanel.removeAll();
        JLabel lbl = new JLabel("AI is selecting a card...");
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Arial", Font.ITALIC, 16));
        handPanel.add(lbl);
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void renderStaminaRest() {
        statusLabel.setText(humanPlayer.getName() + " is EXHAUSTED — must Rest this turn!");
        statusLabel.setForeground(Color.ORANGE);

        handPanel.removeAll();
        JButton restBtn = new JButton("You are exhausted — click to Rest (+3 Stamina, turn skipped)");
        restBtn.setBackground(new Color(160, 60, 0));
        restBtn.setForeground(Color.WHITE);
        restBtn.setFont(new Font("Arial", Font.BOLD, 13));
        restBtn.setFocusPainted(false);
        restBtn.addActionListener(e -> {
            controller.playNextTurn();
            refreshDisplay();
            maybeScheduleAITurn();
        });
        handPanel.add(restBtn);
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void renderSafetyBlock() {
        statusLabel.setText(humanPlayer.getName() + " is BLOCKED by Safety!");
        statusLabel.setForeground(Color.ORANGE);

        handPanel.removeAll();
        JButton skipBtn = new JButton("Blocked by Safety — Click to Skip Your Turn");
        skipBtn.setBackground(new Color(180, 70, 70));
        skipBtn.setForeground(Color.WHITE);
        skipBtn.setFont(new Font("Arial", Font.BOLD, 13));
        skipBtn.setFocusPainted(false);
        skipBtn.addActionListener(e -> {
            // playNextTurn() will detect safetyActive and skip internally
            controller.playNextTurn();
            refreshDisplay();
            maybeScheduleAITurn();
        });
        handPanel.add(skipBtn);
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void renderSnookered() {
        boolean hasEscape = humanPlayer.getHand().getCards().stream()
            .anyMatch(c -> c instanceof EscapeCard);

        statusLabel.setText("YOU ARE SNOOKERED!");
        statusLabel.setForeground(Color.ORANGE);

        handPanel.removeAll();
        String msg = hasEscape
            ? "You have an Escape Card — it will be played automatically. No foul!"
            : "No Escape Card in hand — a 4-point foul will be awarded to the AI.";
        JLabel infoLbl = new JLabel(
            "<html><center><b>SNOOKER!</b><br/>" + msg + "</center></html>",
            SwingConstants.CENTER);
        infoLbl.setForeground(Color.WHITE);
        infoLbl.setFont(new Font("Arial", Font.BOLD, 14));
        handPanel.add(infoLbl);

        JButton resolveBtn = new JButton(hasEscape
            ? "Play Escape Card — Evade Snooker"
            : "Accept Foul — 4 pts to " + aiPlayer.getName());
        resolveBtn.setBackground(hasEscape ? new Color(0, 140, 80) : new Color(180, 50, 50));
        resolveBtn.setForeground(Color.WHITE);
        resolveBtn.setFont(new Font("Arial", Font.BOLD, 13));
        resolveBtn.setFocusPainted(false);
        resolveBtn.addActionListener(e -> {
            // Player.playTurn() detects isSnookered() and auto-resolves
            controller.playNextTurn();
            refreshDisplay();
            maybeScheduleAITurn();
        });
        handPanel.add(resolveBtn);
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void renderGameOver() {
        // Record frame result exactly once — refreshDisplay() can be called multiple times
        if (!frameResultRecorded) {
            frameResultRecorded = true;
            String fw = controller.getWinnerName();
            if (fw.equals(humanPlayer.getName()))  framesWon[0]++;
            else if (fw.equals(aiPlayer.getName())) framesWon[1]++;
            // "Nobody (Draw!)" → no frame point
        }

        String endReason   = controller.getEndReason();
        String frameWinner = controller.getWinnerName();
        statusLabel.setText("FRAME OVER — " + endReason);
        statusLabel.setForeground(Color.RED);

        ScoreBoard sb  = state.getScoreBoard();
        int humanScore = sb.getScore(humanPlayer.getName());
        int aiScore    = sb.getScore(aiPlayer.getName());

        int     needed    = (bestOf / 2) + 1;
        boolean matchOver = framesWon[0] >= needed || framesWon[1] >= needed;
        String  matchWinner = framesWon[0] >= needed ? humanPlayer.getName()
                            : framesWon[1] >= needed ? aiPlayer.getName()
                            : null;

        handPanel.removeAll();

        // Frame result label
        JLabel frameLbl = new JLabel(
            "<html><center>" +
            (bestOf > 1 ? "<b>Frame Winner: " + frameWinner + "</b><br/>" : "<b>Winner: " + frameWinner + "</b><br/>") +
            humanPlayer.getName() + ": " + humanScore + " pts &nbsp;|&nbsp; " +
            aiPlayer.getName()   + ": " + aiScore    + " pts<br/>" +
            "<font color='#FFD700'><i>" + endReason + "</i></font>" +
            (bestOf > 1 ? "<br/>Frames: " + humanPlayer.getName() + " <b>" + framesWon[0] + "</b> — <b>"
                        + framesWon[1] + "</b> " + aiPlayer.getName() : "") +
            "</center></html>",
            SwingConstants.CENTER);
        frameLbl.setForeground(Color.WHITE);
        frameLbl.setFont(new Font("Arial", Font.BOLD, 16));
        handPanel.add(frameLbl);

        if (matchOver) {
            JLabel matchLbl = new JLabel(
                "<html><center><font color='#FFD700' size='+1'>🏆 MATCH WINNER: "
                + matchWinner + "</font></center></html>",
                SwingConstants.CENTER);
            matchLbl.setFont(new Font("Arial", Font.BOLD, 18));
            handPanel.add(matchLbl);

            JButton newMatchBtn = new JButton("New Match");
            newMatchBtn.setBackground(new Color(0, 100, 50));
            newMatchBtn.setForeground(Color.WHITE);
            newMatchBtn.setFont(new Font("Arial", Font.BOLD, 14));
            newMatchBtn.setFocusPainted(false);
            newMatchBtn.addActionListener(e -> {
                framesWon = new int[]{0, 0};
                cardLayout.show(mainPanel, "ruleSelection");
            });
            handPanel.add(newMatchBtn);
        } else {
            int framesLeft = needed - Math.max(framesWon[0], framesWon[1]);
            JLabel progressLbl = new JLabel(
                "First to " + needed + " frames wins the match — " + framesLeft + " frame(s) to go!",
                SwingConstants.CENTER);
            progressLbl.setForeground(new Color(180, 230, 180));
            progressLbl.setFont(new Font("Arial", Font.PLAIN, 12));
            handPanel.add(progressLbl);

            JButton nextFrameBtn = new JButton("Next Frame →");
            nextFrameBtn.setBackground(new Color(50, 130, 200));
            nextFrameBtn.setForeground(Color.WHITE);
            nextFrameBtn.setFont(new Font("Arial", Font.BOLD, 14));
            nextFrameBtn.setFocusPainted(false);
            nextFrameBtn.addActionListener(e -> startNextFrame());
            handPanel.add(nextFrameBtn);
        }

        handPanel.revalidate();
        handPanel.repaint();
    }

    private void refreshScorePanel() {
        scorePanel.removeAll();
        ScoreBoard sb = state.getScoreBoard();

        // Best-of match score at the top
        if (bestOf > 1) {
            JLabel frameLbl = new JLabel(
                "Frames  " + framesWon[0] + " — " + framesWon[1],
                SwingConstants.CENTER);
            frameLbl.setFont(new Font("Arial", Font.BOLD, 14));
            frameLbl.setForeground(new Color(212, 175, 55));
            scorePanel.add(frameLbl);

            int needed = (bestOf / 2) + 1;
            JLabel boLbl = new JLabel("Best of " + bestOf + "  (need " + needed + ")",
                SwingConstants.CENTER);
            boLbl.setFont(new Font("Arial", Font.PLAIN, 10));
            boLbl.setForeground(new Color(120, 120, 120));
            scorePanel.add(boLbl);
            scorePanel.add(new JLabel(" ", SwingConstants.CENTER));
        }

        for (Player player : new Player[]{humanPlayer, aiPlayer}) {
            JLabel scoreLbl = new JLabel(player.getName() + ":  " + sb.getScore(player.getName()) + " pts",
                                         SwingConstants.CENTER);
            scoreLbl.setForeground(Color.WHITE);
            scoreLbl.setFont(new Font("Arial", Font.BOLD, 14));
            scorePanel.add(scoreLbl);

            // Stamina bar — shows remaining energy as a coloured label
            int st = player.getStamina();
            JLabel staminaLbl = new JLabel("Stamina: " + st + "/20", SwingConstants.CENTER);
            staminaLbl.setFont(new Font("Arial", Font.PLAIN, 11));
            staminaLbl.setForeground(st < 5 ? new Color(255, 100, 60)
                                   : st < 10 ? new Color(255, 210, 60)
                                   :            new Color(100, 230, 100));
            scorePanel.add(staminaLbl);
        }

        // Reds on table & phase
        scorePanel.add(new JLabel(" ", SwingConstants.CENTER));   // spacer row
        int reds = state.getRedsOnTable();
        JLabel redsLbl = new JLabel("Reds: " + reds, SwingConstants.CENTER);
        redsLbl.setFont(new Font("Arial", Font.BOLD, 13));
        redsLbl.setForeground(reds == 0 ? new Color(255, 80, 60) : new Color(255, 130, 130));
        scorePanel.add(redsLbl);

        boolean clearance = state.isInClearanceMode();
        JLabel phaseLbl = new JLabel(clearance ? "PHASE: CLEARANCE" : "PHASE: STANDARD",
                                     SwingConstants.CENTER);
        phaseLbl.setFont(new Font("Arial", Font.BOLD, 12));
        phaseLbl.setForeground(clearance ? new Color(255, 210, 60) : new Color(130, 255, 130));
        scorePanel.add(phaseLbl);

        // Deck status — colour shifts from blue → yellow → red as deck runs low
        scorePanel.add(new JLabel(" ", SwingConstants.CENTER));   // spacer row
        int remaining = state.getDeck().size();
        JLabel deckLbl = new JLabel("Deck: " + remaining + " left", SwingConstants.CENTER);
        deckLbl.setFont(new Font("Arial", Font.BOLD, 13));
        deckLbl.setForeground(remaining <= 3 ? new Color(255, 80,  60)
                            : remaining <= 8 ? new Color(255, 210, 60)
                            :                  new Color(130, 210, 255));
        scorePanel.add(deckLbl);

        scorePanel.revalidate();
        scorePanel.repaint();
    }

    /**
     * Redraws the opponent panel using only getCards().size() — the actual
     * Card objects are never read, preserving encapsulation of the AI hand.
     */
    private void refreshOpponentPanel() {
        opponentPanel.removeAll();

        // Only the count is observed — no card names or descriptions are accessed
        int cardCount = aiPlayer.getHand().getCards().size();

        JLabel countLabel = new JLabel(
                "Opponent Hand: " + cardCount + " card" + (cardCount != 1 ? "s" : ""),
                SwingConstants.CENTER);
        countLabel.setForeground(new Color(210, 210, 210));
        countLabel.setFont(new Font("Arial", Font.BOLD, 11));
        opponentPanel.add(countLabel, BorderLayout.NORTH);

        // One gray rectangle per card — face-down, content hidden
        JPanel cardsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        cardsRow.setBackground(new Color(0, 55, 0));
        for (int i = 0; i < cardCount; i++) {
            JPanel cardBack = new JPanel();
            cardBack.setPreferredSize(new Dimension(22, 36));
            cardBack.setBackground(new Color(75, 75, 88));
            cardBack.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 135), 1));
            cardsRow.add(cardBack);
        }
        opponentPanel.add(cardsRow, BorderLayout.CENTER);

        opponentPanel.revalidate();
        opponentPanel.repaint();
    }

    // =========================================================================
    // Event handling — the "C ↔ V" bridge in MVC
    // =========================================================================

    /**
     * Called when the human clicks a card button.
     *
     * Pattern in action:
     *   1. Inject the choice into GUIPlayer  (Dependency Injection within the turn)
     *   2. Tell the controller to advance     (Tell, Don't Ask)
     *   3. Refresh the view                  (Model → View sync)
     *   4. If AI must go next, schedule it   (event-driven, non-blocking)
     */
    private void onCardPlayed(Card card) {
        humanPlayer.selectCard(card);    // inject card choice into the player object
        controller.playNextTurn();       // tell controller: advance one turn
        refreshDisplay();                // sync view with updated model
        maybeScheduleAITurn();           // auto-play AI if it is now the AI's turn
    }

    /**
     * Schedules the AI's turn on a short timer so the user can see
     * the "AI thinking…" message before the board updates.
     */
    private void scheduleAITurn() {
        Timer t = new Timer(900, e -> {
            controller.playNextTurn();
            refreshDisplay();
            // In a 2-player game this is never needed, but kept for correctness
            maybeScheduleAITurn();
        });
        t.setRepeats(false);
        t.start();
    }

    private void maybeScheduleAITurn() {
        if (!controller.isGameOver() && controller.getCurrentPlayer() == aiPlayer) {
            scheduleAITurn();
        }
    }

    // =========================================================================
    // Card button factory
    // =========================================================================

    private JButton makeCardButton(Card card) {
        JButton btn = new JButton(
            "<html><center><b>" + card.getName() + "</b><br/>" +
            "<small>" + card.getDescription() + "</small></center></html>");
        btn.setPreferredSize(new Dimension(125, 80));
        btn.setBackground(cardColour(card));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.PLAIN, 11));
        return btn;
    }

    /** Maps each card name to a snooker-themed colour. */
    private Color cardColour(Card card) {
        return switch (card.getName()) {
            case "Red"      -> new Color(200,  50,  50);
            case "Yellow"   -> new Color(210, 175,   0);
            case "Green"    -> new Color(  0, 150,  50);
            case "Brown"    -> new Color(130,  70,  30);
            case "Blue"     -> new Color(  0, 100, 200);
            case "Pink"     -> new Color(220, 100, 160);
            case "Black"    -> new Color( 30,  30,  30);
            case "Safety"   -> new Color(100, 100, 220);
            case "FreeBall" -> new Color(200, 140,   0);
            case "Magnet"   -> new Color(160,  60, 180);
            case "Glue"     -> new Color( 80, 160, 160);
            case "Snooker"  -> new Color(180,  50, 130);
            case "Escape"   -> new Color( 30, 160, 180);
            default         -> new Color(100, 100, 100);
        };
    }

    // =========================================================================
    // Stamina Challenge (GUI equivalent of HumanPlayer.staminaChallenge())
    // =========================================================================

    /**
     * Presents a multiplication question in a dialog. Correct answer restores 5 stamina.
     * Mirrors the Scanner-based challenge in HumanPlayer, adapted for event-driven GUI.
     * The player object's state is mutated via restoreStamina() — encapsulation preserved.
     */
    private void triggerGUIStaminaChallenge() {
        Random rand = new Random();
        int a = rand.nextInt(10) + 1;
        int b = rand.nextInt(10) + 1;
        String answer = JOptionPane.showInputDialog(this,
            "STAMINA CHALLENGE!\nWhat is " + a + " x " + b + "?",
            "Stamina Challenge — " + humanPlayer.getName(),
            JOptionPane.QUESTION_MESSAGE);
        if (answer == null) return;
        try {
            if (Integer.parseInt(answer.trim()) == a * b) {
                humanPlayer.restoreStamina(5);
                appendLog("Correct! +" + 5 + " stamina restored. Now at "
                    + humanPlayer.getStamina() + "/20.");
            } else {
                appendLog("Wrong! (" + a + " x " + b + " = " + (a * b) + "). No stamina restored.");
            }
        } catch (NumberFormatException e) {
            appendLog("Invalid input — no stamina restored.");
        }
        refreshDisplay();
    }

    // =========================================================================
    // Logging
    // =========================================================================

    private void appendLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}

// =============================================================================
// GUIPlayer — concrete Player subclass that bridges GUI events and game logic.
//
// The Template Method pattern in Player requires subclasses to implement
// chooseCard(). GUIPlayer stores the card pre-selected by the GUI and returns
// it when the game engine asks, keeping the engine completely unaware of Swing.
// =============================================================================

class GUIPlayer extends Player {

    private Card selectedCard;

    public GUIPlayer(String name) { super(name); }

    /**
     * Called by SnookerGUI before triggering controller.playNextTurn().
     * Injects the user's choice without the engine needing to know about buttons.
     */
    public void selectCard(Card card) { this.selectedCard = card; }

    /**
     * Template Method implementation: returns the card the GUI has already
     * chosen. The game engine calls this inside playTurn() — it never needs
     * to know that the choice came from a button click.
     */
    @Override
    public Card chooseCard(GameState state) {
        if (selectedCard == null) {
            throw new IllegalStateException(
                "GUIPlayer.chooseCard() called before selectCard() — " +
                "ensure the GUI sets a card before triggering playNextTurn().");
        }
        Card chosen = selectedCard;
        selectedCard = null;   // consume: each selectCard() corresponds to one turn
        return chosen;
    }
}