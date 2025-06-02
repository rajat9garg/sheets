-- Remove the foreign key constraint from sheets table
ALTER TABLE sheets DROP CONSTRAINT sheets_user_id_fkey;

-- Keep the column but without the foreign key constraint
COMMENT ON COLUMN sheets.user_id IS 'User ID of the sheet owner (no foreign key constraint for testing purposes)';
