-- Step 8b: profile photo storage key (object path or relative key; served via signed URL).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_key TEXT;

COMMENT ON COLUMN users.avatar_key IS 'Storage key for profile photo; never store secrets here.';
