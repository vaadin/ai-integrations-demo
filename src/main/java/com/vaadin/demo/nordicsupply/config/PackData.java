package com.vaadin.demo.nordicsupply.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * The files of the Nordic Supply dataset under {@code classpath:data/nordic_supply/}: the schema and loader scripts,
 * the CSV files, and the customer messages written from them under {@code documents/}. One bean knows where the
 * dataset lives so that nothing else in the application resolves classpath paths of its own.
 */
public class PackData {

    private static final String ROOT = "data/nordic_supply/";

    /** A file of the dataset as text, by its path inside the dataset, e.g. {@code sql/schema-h2.sql}. */
    public String read(String relative) throws IOException {
        return resource(relative).getContentAsString(StandardCharsets.UTF_8);
    }

    /** A file of the dataset as a resource, by its path inside the dataset. */
    public Resource resource(String relative) {
        return new ClassPathResource(ROOT + relative);
    }
}
