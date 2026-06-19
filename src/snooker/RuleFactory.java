package snooker;

/**
 * RuleFactory — maps a user-facing RuleType enum to its IGameRule implementation.
 *
 * Open/Closed Principle: new rule sets are added by extending the enum and
 * adding a case to create(); existing rules and callers are never modified.
 *
 * Dependency Inversion: the GUI depends on this abstraction rather than
 * constructing concrete rule classes directly.
 */
public class RuleFactory {

    public enum RuleType {
        CLASSIC(
            "Classic Rules",
            "Hardcore snooker: Red→Colour enforced, 7pt fouls, clearance in Yellow→Black order."
        ),
        CHAOS(
            "Chaos Mode",
            "Any card, any time! Playing in-sequence doubles your points. Pure aggression wins."
        ),
        ENDURANCE(
            "Endurance Mode",
            "Strict Red→Colour + clearance order. Stamina drains x2 — manage it or lose."
        ),
        CUSTOM(
            "Custom Rules",
            "Compose a brand-new rule: pick a Validity Policy, Scoring Policy, and Foul Behavior."
        );

        private final String displayName;
        private final String description;

        RuleType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /** Returns the IGameRule implementation for the given rule type. */
    public static IGameRule create(RuleType type) {
        return switch (type) {
            case CLASSIC   -> new SnookerRule();
            case CHAOS     -> new ChaosModeRule();
            case ENDURANCE -> new EnduranceModeRule();
            case CUSTOM    -> new ComposableRuleBuilder().build();  // default policies; normally use overload below
        };
    }

    /**
     * Overloaded factory — builds a ComposableRule from a pre-configured ComposableRuleBuilder.
     * For all other RuleTypes the builder is ignored and the standard rule is returned.
     * Demonstrates ad-hoc polymorphism (overloading): same method name, different signature.
     * The builder carries behavioural strategies (enums), not just numeric parameters —
     * so each call can produce a genuinely new rule, not just a tuned version of an existing one.
     */
    public static IGameRule create(RuleType type, ComposableRuleBuilder builder) {
        if (type == RuleType.CUSTOM) return builder.build();
        return create(type);
    }
}