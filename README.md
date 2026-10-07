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

## Deploying

The application is a public demo: there is no login, and the user selector only picks the data scope. Whoever reaches
the URL can ask the model, so what a visitor may spend is bounded in layers, and the outermost layer is not in this
repository:

1. **A spending limit on the key.** Give the deployment an OpenAI project and key of its own, separate from
   development, set a monthly spending limit on the project with *Enforce a hard limit* on, and alerts below it. This
   is the one bound that holds whatever happens in the application, including a restart that resets the counters
   below; enforcement may let a little through while it propagates.
2. **A budget in the application**, `app.ai.*` in `application.properties`, checked before anything is sent to the
   model. A visitor is known by their address (an IPv4 address, or an IPv6 /64 prefix): a session costs nothing to
   replace, so it carries no cap and is only named in the log, and the address and the instance carry the caps. Each
   cap is a property, set from the environment as Spring Boot maps it (`app.ai.instance.tokens-per-day` is
   `APP_AI_INSTANCE_TOKENS_PER_DAY`):

   | Property | Default | What it caps |
   |---|---|---|
   | `app.ai.enabled` | `true` | the kill switch: `false` refuses every turn with a word to the visitor |
   | `app.ai.address.turns-per-day` | 500 | one address, all its sessions together: an office behind one address, not one person |
   | `app.ai.address.concurrent-turns` | 10 | turns one address has under way at once |
   | `app.ai.instance.tokens-per-day` | 2,000,000,000 | the instance, everyone together, as the model reports its tokens |
   | `app.ai.instance.tokens-per-unreported-turn` | 20,000 | what a turn counts for when the model reported no tokens: one that failed, or whose end never came |
   | `app.ai.turn-deadline` | 45m | how long a turn may stay open before it is let go; keep it above the longest turn the call bounds allow |
   | `app.ai.instance.concurrent-turns` | 50 | turns the instance has under way at once |
   | `app.ai.max-question-length` | 500 | characters in a question to a widget |
   | `app.ai.max-text-length` | 20,000 | characters of text in a message to the claim form, pasted or uploaded |

   Size `tokens-per-day` from the key's monthly limit and the model's price; it is what stops a visitor who comes
   from many addresses. A chart turn costs some 35,000 tokens and a claim some 70,000, nearly all of them input, so
   the default is about thirty thousand turns a day. The counts live in memory and start over with the instance. A turn is counted when its model
   request starts. One whose end never comes is let go at the deadline: its slots are freed and it is counted for the
   allowance, and a response that arrives after all is counted in the allowance's place. Nothing can stop the model
   work of a turn let go, so keep the deadline above the longest turn the call bounds allow: the tool calls of a turn
   plus one, times the timeout, times the attempts per call. While the instance's day budget is spent, or the switch
   is off, the Insights and Claims pages say so under their headings.
3. **Bounds on one model call**, also in `application.properties`: the longest reply
   (`AI_MAX_COMPLETION_TOKENS`, 8000), the tool calls a turn may make (`AI_MAX_TOOL_CALLS`, 20, and
   `AI_MAX_CALLS_PER_TOOL`, 10), the OpenAI client's own retries of a failed call (`AI_MAX_RETRIES`, 1; a quota
   error is retried like any other), and a timeout. A file for the claim form is at most 10 MB, checked in the
   browser and again on the server, and a PDF reaches the model cut to the ten pages the form shows.

Behind a reverse proxy, the visitor's address comes in `X-Forwarded-For`; `server.forward-headers-strategy=native`
reads it, from a proxy on a private address out of the box. A proxy on a public address has to be named in
`server.tomcat.remoteip.trusted-proxies`, or every visitor behind it counts as one. Without a proxy nothing is read
from the header.

Every turn and every refusal is one line in the log at `INFO`, with the feature, a short hash of the session, the
address, and for a turn its outcome, the tokens the model reported and its duration; never the question. The SQL the
model ran is logged at `DEBUG` by `com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider`.

## Tests

```bash
mvn verify                                # unit tests plus the Spotless format check
```

Java is formatted with Spotless and Palantir Java Format; `mvn spotless:apply` formats before a commit. The
`build` workflow runs `mvn verify` on every pull request and on every push to `main`.
