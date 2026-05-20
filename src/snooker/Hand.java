package snooker;
import java.util.*;

public class Hand {
    private final List<Card> cards = new ArrayList<>();
    public void addCard(Card c) { cards.add(c); }
    public Card removeCard(int index) { return cards.remove(index); }
    public List<Card> getCards() { return Collections.unmodifiableList(cards); }
    public void print() { cards.forEach(c -> System.out.println(" - " + c)); }
}