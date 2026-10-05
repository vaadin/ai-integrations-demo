package com.vaadin.demo.nordicsupply.ui.views;

import java.util.stream.Stream;

import com.vaadin.flow.component.Component;

/** How the tests find what a view is made of: a component and all the components under it. */
final class ComponentTree {

    private ComponentTree() {}

    static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(ComponentTree::descendants));
    }
}
