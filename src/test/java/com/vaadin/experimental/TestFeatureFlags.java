package com.vaadin.experimental;

/**
 * Lets a test put feature flags into a Vaadin context. Flow keeps them under a wrapper that only this package can
 * construct, which is why this helper lives here.
 */
public final class TestFeatureFlags {

    private TestFeatureFlags() {}

    /** The context attribute Flow reads the flags from. */
    public static Class<?> attributeType() {
        return FeatureFlags.FeatureFlagsWrapper.class;
    }

    /** The context attribute holding these flags. */
    public static Object attribute(FeatureFlags flags) {
        return new FeatureFlags.FeatureFlagsWrapper(flags);
    }
}
