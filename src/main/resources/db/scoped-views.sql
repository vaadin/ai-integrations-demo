-- The data scope the model's queries run under, enforced by the database. The loader runs this script once per
-- region, and once per country for the Insights country filter, with the schema, the account and the scope's
-- countries filled in: one schema of views per read-only account, each view filtering by the countries written into
-- it, and the account may read that schema and nothing else. A table without customers in it is visible in full.
-- The views keep the base tables' names, so the schema text the model reads needs no change.
-- The countries are literals on purpose: a view that reads CURRENT_USER() or a session variable cannot be cached
-- by H2, so a subquery over it is re-run for every outer row and a natural question times out.
-- In PostgreSQL the same design is a row-level security policy per role on the base tables.

CREATE SCHEMA ${schema};

CREATE VIEW ${schema}.warehouses AS SELECT t.* FROM public.warehouses t;
CREATE VIEW ${schema}.categories AS SELECT t.* FROM public.categories t;
CREATE VIEW ${schema}.suppliers AS SELECT t.* FROM public.suppliers t;
CREATE VIEW ${schema}.products AS SELECT t.* FROM public.products t;
CREATE VIEW ${schema}.price_history AS SELECT t.* FROM public.price_history t;
CREATE VIEW ${schema}.promotions AS SELECT t.* FROM public.promotions t;
CREATE VIEW ${schema}.inventory AS SELECT t.* FROM public.inventory t;
CREATE VIEW ${schema}.customers AS
  SELECT t.* FROM public.customers t
  WHERE t.country IN (${countries});
CREATE VIEW ${schema}.delivery_addresses AS
  SELECT t.* FROM public.delivery_addresses t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.orders AS
  SELECT t.* FROM public.orders t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.order_lines AS
  SELECT t.* FROM public.order_lines t
  JOIN public.orders o ON o.id = t.order_id
  JOIN public.customers c ON c.id = o.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.shipments AS
  SELECT t.* FROM public.shipments t
  JOIN public.orders o ON o.id = t.order_id
  JOIN public.customers c ON c.id = o.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.shipment_lines AS
  SELECT t.* FROM public.shipment_lines t
  JOIN public.shipments s ON s.id = t.shipment_id
  JOIN public.orders o ON o.id = s.order_id
  JOIN public.customers c ON c.id = o.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.delivery_events AS
  SELECT t.* FROM public.delivery_events t
  JOIN public.shipments s ON s.id = t.shipment_id
  JOIN public.orders o ON o.id = s.order_id
  JOIN public.customers c ON c.id = o.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.claims AS
  SELECT t.* FROM public.claims t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.claim_lines AS
  SELECT t.* FROM public.claim_lines t
  JOIN public.claims k ON k.id = t.claim_id
  JOIN public.customers c ON c.id = k.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.return_authorisations AS
  SELECT t.* FROM public.return_authorisations t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.return_lines AS
  SELECT t.* FROM public.return_lines t
  JOIN public.return_authorisations r ON r.id = t.return_id
  JOIN public.customers c ON c.id = r.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.credit_notes AS
  SELECT t.* FROM public.credit_notes t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.stock_movements AS SELECT t.* FROM public.stock_movements t;
CREATE VIEW ${schema}.product_current_prices AS SELECT t.* FROM public.product_current_prices t;
CREATE VIEW ${schema}.late_shipments AS
  SELECT t.* FROM public.late_shipments t
  JOIN public.customers c ON c.id = t.customer_id
  WHERE c.country IN (${countries});
CREATE VIEW ${schema}.open_claims AS
  SELECT t.* FROM public.open_claims t
  WHERE t.customer_country IN (${countries});
CREATE VIEW ${schema}.staff AS SELECT t.* FROM public.staff t;

-- The account that reads this schema and nothing else. The password is the demo's: the database is in memory and
-- reachable only from the application, whose users switch between the regions freely. A deployment on a real
-- database creates the accounts and keeps their credentials with its other secrets, outside the repository.
CREATE USER ${user} PASSWORD 'reader';
GRANT SELECT ON SCHEMA ${schema} TO ${user};
