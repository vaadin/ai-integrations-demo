# AI integrations demo

A Vaadin 25.3 + Spring Boot application that shows the Vaadin AI components and Spring AI doing real work in an
application people recognise: the back office of **Nordic Supply**, a fictional outdoor-equipment wholesaler. Three
business cases, each a view of its own: a self-service dashboard where a question becomes a widget, a claim form the
assistant fills from a customer's message, and a supervised bulk change of the product catalogue.

## Data

The dataset ships with the application under `src/main/resources/data/nordic_supply`: thousands of customers, orders,
products and shipments and more than a year of history, generated rather than hand-written. At start-up it is loaded
into an in-memory H2 database, which takes a few seconds; every start begins from the same data.

## Run

Requirements: Java 21 or newer, Maven 3.9.

```bash
mvn spring-boot:run                       # http://localhost:8080 (PORT=8093 mvn ... for another port)
```

Presenting: `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run` keeps development mode but switches the development
tools and Copilot off, so nothing can appear over the app or catch a click.

### With Docker

To try the demo out without installing Java or Maven, Docker is enough. The image builds the application from the
checkout in production mode, which takes a few minutes:

```bash
docker build -t ai-integrations-demo .
docker run --rm -p 8080:8080 -e OPENAI_API_KEY ai-integrations-demo    # http://localhost:8080
```

`-e OPENAI_API_KEY` hands the container the key from your shell, so set it there first; it never becomes part of the
image. Without a key the application still starts, and each AI request ends in an error message.

Charts and Dashboard are commercial Vaadin components, and the image is built without a Vaadin license, so the
application shows Vaadin's commercial banner at the foot of the page.

## Tests

```bash
mvn verify                                # unit tests plus the Spotless format check
```

Java is formatted with Spotless and Palantir Java Format; `mvn spotless:apply` formats before a commit. The
`build` workflow runs `mvn verify` on every pull request and on every push to `main`.
