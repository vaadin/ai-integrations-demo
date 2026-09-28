-- The demo users: which of the dataset's employees can be signed in, and the region each is responsible for.
-- An application with real accounts replaces this table with the roles of the authenticated user.
CREATE TABLE scope_assignment (
    user_id INT PRIMARY KEY REFERENCES users (id),
    role_scope VARCHAR(16) NOT NULL
);
INSERT INTO scope_assignment (user_id, role_scope) VALUES (1, 'all'), (8, 'fi_ee'), (9, 'se_no'), (10, 'dk_de');
