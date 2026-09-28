package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.router.AfterNavigationEvent;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;

/** The rail's user entry, a button, names the signed-in user and lets the session switch to another demo user. */
class UserSwitcherTest {

    private static final SignedInUser TEST_USER = new SignedInUser(1, "Test User", Scopes.ALL);
    private static final SignedInUser LARS = new SignedInUser(8, "Lars Nygaard", Scopes.FI_EE);

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final Page page = mock(Page.class);

    /** The page the entry is on; a mock of its browser page shows whether it reloads. */
    private final UI ui = new UI() {
        @Override
        public Page getPage() {
            return page;
        }
    };

    /** The entry as the rail shows it on the page, with this user signed in. */
    private UserSwitcher switcher(SignedInUser signedIn) {
        when(currentUser.all()).thenReturn(List.of(TEST_USER, LARS));
        when(currentUser.get()).thenReturn(signedIn);
        var switcher = new UserSwitcher(currentUser);
        ui.add(switcher);
        return switcher;
    }

    @Test
    void theButtonNamesTheSignedInUser() {
        assertThat(switcher(LARS).getText()).isEqualTo("Lars Nygaard");
    }

    @Test
    void theListOffersEveryDemoUserWithTheSignedInOneChosen() {
        switcher(LARS);
        var users = users();

        assertThat(users.getListDataView().getItems()).containsExactly(TEST_USER, LARS);
        assertThat(users.getValue()).isEqualTo(LARS);
    }

    @Test
    void anEmployeeIsShownWithTheirNameAndRegion() {
        switcher(TEST_USER);
        var users = users();

        assertThat(users.getItemRenderer().createComponent(LARS).getElement().getTextRecursively())
                .contains("Lars Nygaard")
                .contains("Finland and Estonia");
        assertThat(users.getItemRenderer()
                        .createComponent(TEST_USER)
                        .getElement()
                        .getTextRecursively())
                .contains("Head office");
    }

    @Test
    void choosingAnotherUserSignsTheSessionInAsThemAndReloadsThePage() {
        switcher(TEST_USER);
        users().setValue(LARS);

        verify(currentUser).set(LARS);
        verify(page).reload();
    }

    /**
     * In the rail, the list would be hidden with it when it folds away on a small window, and a list left open there
     * would hold the page unseen.
     */
    @Test
    void theListIsOnThePageNotInTheRail() {
        var switcher = switcher(TEST_USER);

        assertThat(switcher.getChildren()).noneMatch(Popover.class::isInstance);
        assertThat(ui.getChildren()).anyMatch(Popover.class::isInstance);
    }

    @Test
    void aListStillOpenClosesWhenTheViewChanges() {
        var switcher = switcher(TEST_USER);
        popover().open();

        switcher.afterNavigation(mock(AfterNavigationEvent.class));

        assertThat(popover().isOpened()).isFalse();
    }

    /**
     * Another tab of the session can switch it after this one was drawn, so a tab can name a user the session has
     * left. Choosing the session's user there is still a choice: the tab reloads as them.
     */
    @Test
    void aTabLeftBehindByAnotherTabReloadsAsTheUserChosen() {
        switcher(TEST_USER);
        var users = users();
        when(currentUser.get()).thenReturn(LARS);

        users.setValue(LARS);

        verify(currentUser).set(LARS);
        verify(page).reload();
    }

    /** The list's popover, which Flow adds to the page once the entry is on it. */
    private Popover popover() {
        return (Popover)
                ui.getChildren().filter(Popover.class::isInstance).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ListBox<SignedInUser> users() {
        return (ListBox<SignedInUser>) popover()
                .getChildren()
                .filter(ListBox.class::isInstance)
                .findFirst()
                .orElseThrow();
    }
}
