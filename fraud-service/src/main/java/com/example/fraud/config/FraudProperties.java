package com.example.fraud.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties("fraud")
@Validated
public class FraudProperties {

    @Min(0)
    @Max(100)
    private int flagThreshold = 60;

    @Valid
    @NotNull
    private Rules rules = new Rules();

    public int getFlagThreshold() {
        return flagThreshold;
    }

    public void setFlagThreshold(int flagThreshold) {
        this.flagThreshold = flagThreshold;
    }

    public Rules getRules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }

    @PostConstruct
    void validateWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("high-amount", rules.getHighAmount().getWeight());
        weights.put("velocity", rules.getVelocity().getWeight());
        weights.put("country-mismatch", rules.getCountryMismatch().getWeight());
        weights.put("unusual-hour", rules.getUnusualHour().getWeight());
        RuleWeightValidator.validate(weights);
    }

    public static class Rules {
        @Valid
        private HighAmount highAmount = new HighAmount();
        @Valid
        private Velocity velocity = new Velocity();
        @Valid
        private CountryMismatch countryMismatch = new CountryMismatch();
        @Valid
        private UnusualHour unusualHour = new UnusualHour();

        public HighAmount getHighAmount() {
            return highAmount;
        }

        public void setHighAmount(HighAmount highAmount) {
            this.highAmount = highAmount;
        }

        public Velocity getVelocity() {
            return velocity;
        }

        public void setVelocity(Velocity velocity) {
            this.velocity = velocity;
        }

        public CountryMismatch getCountryMismatch() {
            return countryMismatch;
        }

        public void setCountryMismatch(CountryMismatch countryMismatch) {
            this.countryMismatch = countryMismatch;
        }

        public UnusualHour getUnusualHour() {
            return unusualHour;
        }

        public void setUnusualHour(UnusualHour unusualHour) {
            this.unusualHour = unusualHour;
        }
    }

    /** Settings shared by every rule. */
    public abstract static class RuleSettings {
        private boolean enabled = true;

        @Min(0)
        @Max(100)
        private int weight;

        protected RuleSettings(int defaultWeight) {
            this.weight = defaultWeight;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }

    public static class HighAmount extends RuleSettings {
        @NotNull
        private BigDecimal threshold = new BigDecimal("10000000");

        public HighAmount() {
            super(40);
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }
    }

    public static class Velocity extends RuleSettings {
        @Min(1)
        private int maxCount = 5;
        @Min(1)
        private int windowMinutes = 10;

        public Velocity() {
            super(30);
        }

        public int getMaxCount() {
            return maxCount;
        }

        public void setMaxCount(int maxCount) {
            this.maxCount = maxCount;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }
    }

    public static class CountryMismatch extends RuleSettings {
        @Min(1)
        @Max(1440)
        private int minTravelMinutes = 120;

        public CountryMismatch() {
            super(50);
        }

        public int getMinTravelMinutes() {
            return minTravelMinutes;
        }

        public void setMinTravelMinutes(int minTravelMinutes) {
            this.minTravelMinutes = minTravelMinutes;
        }
    }

    public static class UnusualHour extends RuleSettings {
        @Min(0)
        @Max(24)
        private int startHour = 0;
        @Min(0)
        @Max(24)
        private int endHour = 5;
        @NotNull
        private BigDecimal minAmount = new BigDecimal("5000000");

        public UnusualHour() {
            super(25);
        }

        public int getStartHour() {
            return startHour;
        }

        public void setStartHour(int startHour) {
            this.startHour = startHour;
        }

        public int getEndHour() {
            return endHour;
        }

        public void setEndHour(int endHour) {
            this.endHour = endHour;
        }

        public BigDecimal getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(BigDecimal minAmount) {
            this.minAmount = minAmount;
        }
    }
}