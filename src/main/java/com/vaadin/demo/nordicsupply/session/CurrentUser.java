package com.vaadin.demo.nordicsupply.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.data.ScopeAssignmentRepository;
import com.vaadin.demo.nordicsupply.data.StaffRepository;

/**
 * The employee the session is signed in as. The demo has no login: the rail offers the employees that have a region,
 * a session starts as the head-office one, whose region is every country, and the choice lives in the Vaadin session.
 * Who can be offered, and which region each covers, is data the database holds; an application with real accounts
 * reads the authenticated user instead and changes nothing else.
 */
@Component
@VaadinSessionScope
public class CurrentUser implements Serializable {

    /** An employee the demo can sign in as: a value the session keeps, not the {@code Staff} entity. */
    public record SignedInUser(int id, String name, Scope scope) implements Serializable {}

    private final List<SignedInUser> offered;
    private SignedInUser user;

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
        user = offered.stream()
                .filter(candidate -> Scopes.ALL.equals(candidate.scope()))
                .findFirst()
                .orElseGet(offered::getFirst);
    }

    /** The employees that can be signed in, in the order of their ids. */
    public List<SignedInUser> all() {
        return offered;
    }

    /** The employee the session is signed in as: the head-office one until {@link #set} signs in another. */
    public SignedInUser get() {
        return user;
    }

    /**
     * Signs the session in as another of the offered employees.
     *
     * @throws IllegalArgumentException when the employee is not one the demo offers
     */
    public void set(SignedInUser user) {
        if (!offered.contains(user)) {
            throw new IllegalArgumentException("not a demo user: " + user);
        }
        this.user = user;
    }
}
