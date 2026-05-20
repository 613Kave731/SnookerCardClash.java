package snooker;
import java.util.*;

public class ScoreBoard {
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    public void register(String p) { scores.put(p, 0); }
    // Ad-hoc Polymorphism (Overloading)
    public void addPoints(String p, int pts) { scores.merge(p, pts, Integer::sum); }
    public int getScore(String p) { return scores.getOrDefault(p, 0); }
    public void print() { System.out.println("Scores: " + scores); }
}