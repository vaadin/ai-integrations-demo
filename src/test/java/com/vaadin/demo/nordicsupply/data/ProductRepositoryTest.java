package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** The product queries against the dataset as generated. */
@SpringBootTest
class ProductRepositoryTest {

    private static final PageRequest ALL = PageRequest.of(0, 4_000);

    @Autowired
    private ProductRepository products;

    @Test
    void anEmptyTermMatchesEveryProductWithItsCurrentPrice() {
        var rows = products.search("", ALL).getContent();

        assertThat(rows).hasSize(3_202).allSatisfy(row -> {
            assertThat(row.listPrice()).isPositive();
            assertThat(row.category()).isNotBlank();
            assertThat(row.supplier()).isNotBlank();
        });
    }

    @Test
    void aTermMatchesTheSku() {
        var rows = products.search(SearchTerms.like("ALV-KIT-0001"), ALL).getContent();

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.sku()).isEqualTo("ALV-KIT-0001"));
    }

    @Test
    void aTermMatchesTheName() {
        var rows = products.search(SearchTerms.like("footprint"), ALL).getContent();

        assertThat(rows).hasSize(78).allSatisfy(row -> assertThat(row.name()).containsIgnoringCase("footprint"));
    }

    @Test
    void aTermMatchesTheSupplier() {
        var rows = products.search(SearchTerms.like("Älv Outdoor"), ALL).getContent();

        assertThat(rows)
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.supplier() + " " + row.name()).contains("Älv"));
    }

    @Test
    void aTermMatchesTheCategory() {
        var rows = products.search(SearchTerms.like("Camp Kitchen"), ALL).getContent();

        assertThat(rows)
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.category()).isEqualTo("Camp Kitchen"));
    }
}
