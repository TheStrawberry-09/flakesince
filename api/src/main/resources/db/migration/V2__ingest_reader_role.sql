DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ingest_reader') THEN
CREATE ROLE ingest_reader NOLOGIN;
END IF;
END
$$;

GRANT SELECT (project_id, key_hash, revoked_at) ON api_key TO ingest_reader;