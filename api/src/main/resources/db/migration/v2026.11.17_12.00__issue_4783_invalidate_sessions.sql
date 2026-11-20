-- Invalidate the stored web sessions once, on upgrade.
--
-- Spring Session keeps the security context in the database with Java serialization, and the
-- UserContext inside it gained fields for the token exchange. The runtime derives a class
-- identifier from the shape of that class, so a session written by an earlier build can no longer
-- be read: its holder meets a deserialization error rather than a login prompt. Removing the
-- stored sessions here means nobody meets that error.
--
-- Two limits of doing it this way, both of which need a stop-the-world restart to avoid:
--
--   * A rolling upgrade defeats it. While an old replica is still serving it keeps writing
--     old-shape sessions after this has run, and their holders meet the error as soon as they are
--     routed to a new replica. Take the old replicas out of service before the new ones start.
--   * A downgrade is not covered. This migration is recorded once and does not run again, and the
--     earlier build has no migration of its own, so rolling the API back leaves sessions it cannot
--     read. Clear them by hand if that happens.
--
-- Guarded rather than written as a plain DELETE. The session tables are created by Spring Session
-- at start-up, not by these migrations, and nothing orders that against Flyway — so on a new
-- environment this may run before they exist, and an unguarded statement would fail the migration
-- and stop the service from starting. to_regclass resolves against the connection's search_path,
-- which Flyway sets from flyway.schemas, so this works whatever the schema is called; naming the
-- schema here would silently skip the delete wherever it is not 'pipeline'.
--
-- The attribute rows go first in case the foreign key between the two tables was created without
-- ON DELETE CASCADE.
DO $$
BEGIN
    IF to_regclass('spring_session_attributes') IS NOT NULL THEN
        DELETE FROM spring_session_attributes;
    END IF;

    IF to_regclass('spring_session') IS NOT NULL THEN
        DELETE FROM spring_session;
    END IF;
END $$;
