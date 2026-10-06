package com.vaadin.demo.nordicsupply.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.data.ScopeAssignmentRepository;
import com.vaadin.demo.nordicsupply.data.StaffRepository;

/**
 * The employee the session is signed in as, held in a shared signal so that every tab of the session follows a
 * switch made in any of them. The demo has no login: the rail offers the employees that have a region, and a session
 * starts as the head-office one, whose region is every country. Who can be offered, and which region each covers, is
 * data the database holds; an application with real accounts reads the authenticated user instead and changes
 * nothing else.
 * <p>
 * The session also holds the Insights country filter, the part of the region the dashboard shows: the whole region,
 * or one of its countries. Like the employee it is shared by every tab, it is not saved with a dashboard, and a switch
 * of employee resets it to the new region. A shared signal cannot be serialized, so neither can this bean, which only
 * matters to session replication.
 */
@Component
@VaadinSessionScope
public class CurrentUser {

    /** An employee the demo can sign in as: a value the session keeps, not the {@code Staff} entity. */
    public record SignedInUser(int id, String name, Scope scope) implements Serializable {}

    private final List<SignedInUser> offered;
    private final SharedValueSignal<SignedInUser> user;
    private final SharedValueSignal<Scope> filter;

    public CurrentUser(StaffRepository staff, ScopeAssignmentRepository assignments) {
        var users = new ArrayList<SignedInUser>();
        for (var assignment : assignments.findAllByOrderByUserId()) {
            var scope = Scopes.roleScope(assignment.getRoleScope());
            var person = staff.findById(assignment.getUserId());
            if (scope.isPresent() && person.isPresent()) {
                users.add(new SignedInUser(person.get().getId(), person.get().getFullName(), scope.get()));
            }
        }
        offered = List.copyOf(users);
        user = new SharedValueSignal<>(offered.stream()
                .filter(candidate -> Scopes.ALL.equals(candidate.scope()))
                .findFirst()
                .orElseGet(offered::getFirst));
        filter = new SharedValueSignal<>(user.peek().scope());
    }

    /** The employees that can be signed in, in the order of their ids. */
    public List<SignedInUser> all() {
        return offered;
    }

    /** The signed-in employee as a read-only signal, for the screen to follow. */
    public Signal<SignedInUser> signal() {
        return user.asReadonly();
    }

    /** The employee the session is signed in as right now: the head-office one until {@link #set} signs in another. */
    public SignedInUser get() {
        return user.peek();
    }

    /**
     * Signs the session in as another of the offered employees; every tab of the session follows.
     *
     * @throws IllegalArgumentException when the employee is not one the demo offers
     */
    public void set(SignedInUser next) {
        if (!offered.contains(next)) {
            throw new IllegalArgumentException("not a demo user: " + next);
        }
        user.set(next);
        filter.set(next.scope());
    }

    /** The country filter as a read-only signal, for every tab's dashboard to follow. */
    public Signal<Scope> filterSignal() {
        return filter.asReadonly();
    }

    /**
     * What the Insights dashboard shows right now: the signed-in employee's region, or the one country the filter
     * narrows it to. Never wider than the region, also in the moment between a switch of employee and the reset of
     * the filter that comes with it.
     */
    public Scope filter() {
        var region = get().scope();
        var chosen = filter.peek();
        return Scopes.filtersWithin(region).contains(chosen) ? chosen : region;
    }

    /**
     * Narrows the Insights dashboard to one of the signed-in employee's countries, or widens it back to their region;
     * every tab of the session follows.
     *
     * @throws IllegalArgumentException when the scope is neither the region nor one of its countries
     */
    public void setFilter(Scope scope) {
        var region = get().scope();
        if (!Scopes.filtersWithin(region).contains(scope)) {
            throw new IllegalArgumentException("not within " + region.key() + ": " + scope.key());
        }
        filter.set(scope);
    }
}
