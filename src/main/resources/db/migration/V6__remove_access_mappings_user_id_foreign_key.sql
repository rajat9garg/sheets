-- Remove foreign key constraint on access_mappings.user_id
ALTER TABLE access_mappings DROP CONSTRAINT access_mappings_user_id_fkey;
