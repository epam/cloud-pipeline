-- Invalidate the stored web sessions once, on upgrade — the Spring Boot 3 session tables.
--
-- This finishes what v2026.11.17_12.00 starts. That migration came from the pre-migration line,
-- where Spring Session's tables are called spring_session and spring_session_attributes, so both of
-- its guards resolve to nothing here and it deletes no rows. This branch renamed the tables to
-- spring_3_session and spring_3_session_attributes, so the delete it describes has to be repeated
-- against those names or the stored sessions survive the UserContext shape change and their holders
-- meet a deserialization error rather than a login prompt.
--
-- The earlier migration is left exactly as it arrived: it is already recorded on the branch it came
-- from, and rewriting it would change a checksum there and conflict on every later merge.
--
-- Unlike there, these tables are created by Flyway on this branch
-- (v2026.08.07_18.00__create_jdbc_session_tables.sql), which runs strictly before this, and
-- spring.session.jdbc.initialize-schema is never, so Spring Session does not create them at
-- start-up. The tables therefore always exist by the time this runs and a plain DELETE is safe. The
-- guard is kept only for an environment restored from a dump taken before those tables existed;
-- to_regclass resolves against the connection's search_path, which Flyway sets from
-- flyway.schemas, so naming the schema here would silently skip the delete wherever it is not
-- 'pipeline'.
--
-- The same two limits as the earlier migration apply, and both need a stop-the-world restart to
-- avoid: a rolling upgrade lets an old replica keep writing old-shape sessions after this has run,
-- and a downgrade is not covered, because this is recorded once and does not run again.
--
-- The foreign key on spring_3_session_attributes is ON DELETE CASCADE, so clearing the parent would
-- be enough; the attribute rows are deleted first anyway, to stay correct if that ever changes.
DO $$
BEGIN
    IF to_regclass('spring_3_session_attributes') IS NOT NULL THEN
        DELETE FROM spring_3_session_attributes;
    END IF;

    IF to_regclass('spring_3_session') IS NOT NULL THEN
        DELETE FROM spring_3_session;
    END IF;
END $$;
