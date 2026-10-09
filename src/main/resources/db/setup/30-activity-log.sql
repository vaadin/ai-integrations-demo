-- The activity log is kept per visit. Everyone signs in as one of the same four employees, so a log kept per
-- employee would show one visitor's questions, and the customers' messages they had read, to every later visitor.
-- Each row names the visit that wrote it, a visit reads only its own rows, and they are deleted when its session
-- ends. An application with real accounts needs no such column: the signed-in user is the boundary.
ALTER TABLE activity_log ADD COLUMN visit_id UUID NOT NULL;
CREATE INDEX ix_activity_log_visit ON activity_log (visit_id, occurred_at);
