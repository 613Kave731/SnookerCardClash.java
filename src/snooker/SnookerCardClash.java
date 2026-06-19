package snooker;

import javax.swing.*;

public class SnookerCardClash {
    public static void main(String[] args) {
        GUIPlayer p1 = new GUIPlayer("Ronnie");
        Player    p2 = new AggressiveAI("The Machine");

        // Controller and rule are created inside the GUI after the player picks a rule set

        SwingUtilities.invokeLater(() -> {
            SnookerGUI gui = new SnookerGUI(p1, p2);
            gui.setVisible(true);
        });


    }
}