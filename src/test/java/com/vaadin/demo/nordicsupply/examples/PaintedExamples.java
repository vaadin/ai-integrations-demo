package com.vaadin.demo.nordicsupply.examples;

/** The example documents, painted once for all the tests that read them, since painting takes a moment. */
public final class PaintedExamples {

    public static final ExampleDocuments ALL = new ExampleDocuments();

    private PaintedExamples() {}

    /** The example with the given file name. */
    public static ExampleDocument named(String name) {
        return ALL.all().stream()
                .filter(example -> example.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
