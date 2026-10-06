package com.vaadin.demo.nordicsupply.session;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The regions a demo user can be responsible for: every country, the head office's, or one of three pairs; and the
 * six countries alone, which the Insights country filter narrows a region to. They are fixed in code and named by key
 * in the database; nothing a user types ever becomes one.
 */
public final class Scopes {

    public static final Scope ALL = Scope.of("all", "Head office", "FI", "EE", "SE", "NO", "DK", "DE");
    public static final Scope FI_EE = Scope.of("fi_ee", "Finland and Estonia", "FI", "EE");
    public static final Scope SE_NO = Scope.of("se_no", "Sweden and Norway", "SE", "NO");
    public static final Scope DK_DE = Scope.of("dk_de", "Denmark and Germany", "DK", "DE");

    public static final Scope FI = Scope.of("fi", "Finland", "FI");
    public static final Scope EE = Scope.of("ee", "Estonia", "EE");
    public static final Scope SE = Scope.of("se", "Sweden", "SE");
    public static final Scope NO = Scope.of("no", "Norway", "NO");
    public static final Scope DK = Scope.of("dk", "Denmark", "DK");
    public static final Scope DE = Scope.of("de", "Germany", "DE");

    private static final List<Scope> ROLE_SCOPES = List.of(ALL, FI_EE, SE_NO, DK_DE);

    private static final List<Scope> COUNTRY_SCOPES = List.of(FI, EE, SE, NO, DK, DE);

    private Scopes() {}

    /** The four regions, the head office first. */
    public static List<Scope> roleScopes() {
        return ROLE_SCOPES;
    }

    /** The region a key names, or empty when the key is not one of the four. */
    public static Optional<Scope> roleScope(String key) {
        return ROLE_SCOPES.stream().filter(scope -> scope.key().equals(key)).findFirst();
    }

    /** Every scope the database has a schema and an account for: the four regions, then the six countries. */
    public static List<Scope> all() {
        return Stream.concat(ROLE_SCOPES.stream(), COUNTRY_SCOPES.stream()).toList();
    }

    /**
     * What the country filter offers a region: the region itself, then each of its countries alone, in the head
     * office's order. Nothing in it is wider than the region.
     */
    public static List<Scope> filtersWithin(Scope region) {
        var countries =
                COUNTRY_SCOPES.stream().filter(country -> region.countries().containsAll(country.countries()));
        return Stream.concat(Stream.of(region), countries).toList();
    }
}
