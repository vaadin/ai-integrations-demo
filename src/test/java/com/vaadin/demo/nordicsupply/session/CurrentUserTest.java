package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.vaadin.flow.spring.scopes.VaadinSessionScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.vaadin.demo.nordicsupply.data.ScopeAssignmentRepository;
import com.vaadin.demo.nordicsupply.data.StaffRepository;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;

/** A session offers the four demo users from the database, starts as the head-office one, and can switch. */
@SpringBootTest
class CurrentUserTest {

    @Autowired
    private StaffRepository staff;

    @Autowired
    private ScopeAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConfigurableListableBeanFactory beans;

    private CurrentUser session() {
        return new CurrentUser(staff, assignments);
    }

    @Test
    void theFourDemoUsersAreOfferedWithTheirRegions() {
        assertThat(session().all())
                .extracting(SignedInUser::id, SignedInUser::name, SignedInUser::scope)
                .containsExactly(
                        tuple(1, "Test User", Scopes.ALL),
                        tuple(8, "Lars Nygaard", Scopes.FI_EE),
                        tuple(9, "Helmi Saari", Scopes.SE_NO),
                        tuple(10, "Oskar Berg", Scopes.DK_DE));
    }

    @Test
    void aSessionStartsAsTheHeadOfficeUser() {
        assertThat(session().get().name()).isEqualTo("Test User");
    }

    /** The head-office user is the one responsible for every country, wherever the ids put them in the list. */
    @Test
    @Transactional
    void theHeadOfficeUserIsKnownByTheirRegion() {
        jdbc.update("update scope_assignment set role_scope = case user_id when 1 then 'dk_de' else 'all' end"
                + " where user_id in (1, 10)");

        assertThat(session().get().name()).isEqualTo("Oskar Berg");
    }

    @Test
    void aSessionCanSwitchToAnotherOfferedUser() {
        var session = session();
        var lars = session.all().get(1);

        session.set(lars);

        assertThat(session.get()).isEqualTo(lars);
        assertThat(session.signal().peek()).isEqualTo(lars);
    }

    @Test
    void aSessionCannotSwitchToSomeoneTheDemoDoesNotOffer() {
        var session = session();
        var stranger = new SignedInUser(2, "Someone Else", Scopes.ALL);

        assertThatThrownBy(() -> session.set(stranger)).isInstanceOf(IllegalArgumentException.class);
        assertThat(session.get().name()).isEqualTo("Test User");
    }

    /** Without the session scope the bean is one for everyone, and one visitor's switch signs in everyone else. */
    @Test
    void eachVaadinSessionHasItsOwn() {
        assertThat(beans.getBeanDefinition("currentUser").getScope())
                .isEqualTo(VaadinSessionScope.VAADIN_SESSION_SCOPE_NAME);
    }
}
