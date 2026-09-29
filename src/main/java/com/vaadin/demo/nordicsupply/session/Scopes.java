package com.vaadin.demo.nordicsupply.session;

import java.util.List;
import java.util.Optional;

/**
 * The regions a demo user can be responsible for: every country, the head office's, or one of three pairs. They are
 * fixed in code and named by key in the database; nothing a user types ever becomes one.
 */
public final class Scopes {

    public static final Scope ALL = Scope.of("all", "Head office", "FI", "EE", "SE", "NO", "DK", "DE");
    public static final Scope FI_EE = Scope.of("fi_ee", "Finland and Estonia", "FI", "EE");
    public static final Scope SE_NO = Scope.of("se_no", "Sweden and Norway", "SE", "NO");
    public static final Scope DK_DE = Scope.of("dk_de", "Denmark and Germany", "DK", "DE");

    private static final List<Scope> ROLE_SCOPES = List.of(ALL, FI_EE, SE_NO, DK_DE);

    private Scopes() {}

    /** The region a key names, or empty when the key is not one of the four. */
    public static Optional<Scope> roleScope(String key) {
        return ROLE_SCOPES.stream().filter(scope -> scope.key().equals(key)).findFirst();
    }
}
