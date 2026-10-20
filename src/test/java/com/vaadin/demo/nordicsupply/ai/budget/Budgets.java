package com.vaadin.demo.nordicsupply.ai.budget;

import java.time.Duration;

import org.springframework.util.unit.DataSize;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Instance;

/**
 * Budgets for the tests: the production defaults, or those with one part of the limits replaced. Their turns and
 * refusals go to a usage log that writes nothing, unless a test gives one of its own.
 */
public final class Budgets {

    /** The production defaults, as {@link AIBudgetProperties} declares them. */
    public static final AIBudgetProperties DEFAULTS = new AIBudgetProperties(
            true,
            500,
            20_000,
            DataSize.ofMegabytes(10),
            Duration.ofMinutes(45),
            new Address(500, 50),
            new Instance(400_000_000, 20_000, 200));

    /** A usage log without a directory, which writes nothing. */
    public static final UsageLog NO_USAGE_LOG = new UsageLog("", "test-model", null);

    private Budgets() {}

    /** A budget with the production defaults, generous enough for a test. */
    public static AIBudget budget() {
        return budget(NO_USAGE_LOG);
    }

    /** A budget with the production defaults, whose turns and refusals go to the given usage log. */
    public static AIBudget budget(UsageLog usage) {
        return new AIBudget(DEFAULTS, usage);
    }

    /** A budget with the given limits, whose turns and refusals go nowhere. */
    private static AIBudget unrecorded(AIBudgetProperties limits) {
        return new AIBudget(limits, NO_USAGE_LOG);
    }

    /** The defaults with the address's limits replaced. */
    public static AIBudget withAddress(Address address) {
        return unrecorded(new AIBudgetProperties(
                true,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.maxFileSize(),
                DEFAULTS.turnDeadline(),
                address,
                DEFAULTS.instance()));
    }

    /** The defaults with the instance's limits replaced. */
    public static AIBudget withInstance(Instance instance) {
        return unrecorded(new AIBudgetProperties(
                true,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.maxFileSize(),
                DEFAULTS.turnDeadline(),
                DEFAULTS.address(),
                instance));
    }

    /** The defaults with the file cap replaced. */
    public static AIBudget withMaxFileSize(DataSize maxFileSize) {
        return unrecorded(new AIBudgetProperties(
                true,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                maxFileSize,
                DEFAULTS.turnDeadline(),
                DEFAULTS.address(),
                DEFAULTS.instance()));
    }

    /** The defaults, switched off. */
    public static AIBudget disabled() {
        return unrecorded(new AIBudgetProperties(
                false,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.maxFileSize(),
                DEFAULTS.turnDeadline(),
                DEFAULTS.address(),
                DEFAULTS.instance()));
    }
}
