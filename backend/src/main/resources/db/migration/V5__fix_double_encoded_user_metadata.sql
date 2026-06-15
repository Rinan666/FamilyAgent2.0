-- V5: Fix double-encoded user metadata
--
-- Custom @Select queries in UserRepository were missing @ResultMap,
-- so PgJsonbTypeHandler was bypassed on reads. The metadata column
-- came back as a raw JSON text string, and updateById re-serialized
-- it via the type handler, wrapping valid JSON inside JSON strings.
-- Each login doubled the metadata size until OOM.
--
-- This migration iteratively unwraps JSON-string layers back to the
-- actual JSON object/array value.

DO $$
DECLARE
  rec RECORD;
  fixed INT;
BEGIN
  LOOP
    fixed := 0;
    FOR rec IN SELECT id, metadata FROM users WHERE jsonb_typeof(metadata) = 'string' LOOP
      BEGIN
        UPDATE users
        SET metadata = (rec.metadata #>> '{}')::jsonb,
            updated_at = NOW()
        WHERE id = rec.id;
        fixed := fixed + 1;
      EXCEPTION WHEN OTHERS THEN
        -- Inner text cannot be parsed as JSONB; leave as-is
        RAISE WARNING 'Cannot fix metadata for user id=%', rec.id;
      END;
    END LOOP;
    EXIT WHEN fixed = 0;
  END LOOP;
END;
$$;
