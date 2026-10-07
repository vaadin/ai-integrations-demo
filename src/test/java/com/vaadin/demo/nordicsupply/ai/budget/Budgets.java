package com.vaadin.demo.nordicsupply.ai.budget;

import java.time.Duration;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Instance;

/** Budgets for the tests: the production defaults, or those with one part of the limits replaced. */
public final class Budgets {

    /** The production defaults, as {@link AIBudgetProperties} declares them. */
    public static final AIBudgetProperties DEFAULTS = new AIBudgetProperties(
            true, 500, 20_000, Duration.ofMinutes(20), new Address(120, 3), new Instance(3_000_000, 20_000, 6));

    private Budgets() {}

    /** A budget with the production defaults, generous enough for a test. */
    public static AIBudget budget() {
        return new AIBudget(DEFAULTS);
    }

    /** The defaults with the address's limits replaced. */
    public static AIBudget withAddress(Address address) {
        return new AIBudget(new AIBudgetProperties(
                true,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.turnDeadline(),
                address,
                DEFAULTS.instance()));
    }

    /** The defaults with the instance's limits replaced. */
    public static AIBudget withInstance(Instance instance) {
        return new AIBudget(new AIBudgetProperties(
                true,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.turnDeadline(),
                DEFAULTS.address(),
                instance));
    }

    /** The defaults, switched off. */
    public static AIBudget disabled() {
        return new AIBudget(new AIBudgetProperties(
                false,
                DEFAULTS.maxQuestionLength(),
                DEFAULTS.maxTextLength(),
                DEFAULTS.turnDeadline(),
                DEFAULTS.address(),
                DEFAULTS.instance()));
    }
}
