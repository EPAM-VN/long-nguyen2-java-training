package training.taskforge.model;

import training.taskforge.error.InvalidJobConfigException;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum JobPriority {
    LOW(1) {
        @Override
        public Duration timeBudget() {
            return Duration.ofMinutes(3);
        }
    },

    NORMAL(5) {
        @Override
        public Duration timeBudget() {
            return Duration.ofMinutes(2);
        }
    },

    CRITICAL(10) {
        @Override
        public Duration timeBudget() {
            return Duration.ofMinutes(1);
        }
    };

    private final int weight;

    JobPriority(int weight) {
        this.weight = weight;
    }

    // Higher means "run me sooner"
    public int weight() {
        return weight;
    }

    public abstract Duration timeBudget();

    public static JobPriority parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidJobConfigException(
                    "invalid priority '" + raw + "' (expected " + legalValues() + ")", e);
        }
    }

    private static String legalValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
