-- TEST ONLY: registers a friendly-identifier series for a throwaway test object type, the way a
-- later phase's migration will register Person (P), File (F), etc. Never part of production.
CREATE SEQUENCE friendly_id_seq_test_widget NO CYCLE;
INSERT INTO friendly_id_series (object_type, prefix, min_digits, sequence_name)
VALUES ('test_widget', 'TW', 5, 'friendly_id_seq_test_widget');

CREATE TABLE test_widget (
    widget_id   uuid PRIMARY KEY,
    friendly_id text NOT NULL UNIQUE
);
