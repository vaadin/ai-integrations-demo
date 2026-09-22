package com.vaadin.demo.nordicsupply.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The dataset's files are found by their path inside the dataset. */
class PackDataTest {

    @Test
    void readsAFileOfTheDataset() throws Exception {
        var pack = new PackData();

        assertThat(pack.read("sql/schema-h2.sql")).startsWith("-- Nordic Supply demo schema");
        assertThat(pack.resource("csv/customers.csv").exists()).isTrue();
    }

    @Test
    void aFileTheDatasetDoesNotHaveDoesNotExist() {
        assertThat(new PackData().resource("sql/no-such-file.sql").exists()).isFalse();
    }
}
