package snooker;
import java.util.*;
import java.util.function.Predicate;

// Parametric Polymorphism (Generics)[cite: 2]
public class Deck<T extends Card> {
    private final List<T> cards = new ArrayList<>();
    public void addCard(T card) { cards.add(card); }
    public void shuffle() { Collections.shuffle(cards); }
    public T draw() throws EmptyDeckException {
        if (cards.isEmpty()) throw new EmptyDeckException();
        return cards.remove(0);
    }

    /**
     * Draws the first card that satisfies {@code condition}.
     * Used by MagnetCard to pull a specific card type from the deck
     * without exposing the internal list (Information Hiding).
     */
    public T drawIf(Predicate<T> condition) throws EmptyDeckException {
        for (int i = 0; i < cards.size(); i++) {
            if (condition.test(cards.get(i))) return cards.remove(i);
        }
        throw new EmptyDeckException();
    }

    public int size() { return cards.size(); }
}
