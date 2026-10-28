-- TEST ONLY: creates a table and then fails, to prove the whole migration rolls back (UP-005).
CREATE TABLE must_not_survive (id integer);
SELECT * FROM relation_that_does_not_exist;
