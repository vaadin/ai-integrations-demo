# AI integrations demo

A Vaadin 25.3 + Spring Boot application that shows the Vaadin AI components and Spring AI doing real work in an
application people recognise: the back office of **Nordic Supply**, a fictional outdoor-equipment wholesaler. Three
business cases, each a view of its own: a self-service dashboard where a question becomes a widget, a claim form the
assistant fills from a customer's message, and a supervised bulk change of the product catalogue.

## Run

Requirements: Java 21 or newer, Maven 3.9.

```bash
mvn spring-boot:run                       # http://localhost:8080 (PORT=8093 mvn ... for another port)
```

Presenting: `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run` keeps development mode but switches the development
tools and Copilot off, so nothing can appear over the app or catch a click.

## Tests

```bash
mvn verify                                # unit tests plus the Spotless format check
```

Java is formatted with Spotless and Palantir Java Format; `mvn spotless:apply` formats before a commit. The
`build` workflow runs `mvn verify` on every pull request and on every push to `main`.
# ai-integrations-demo
