-- We need to recreate the enum type with all values including OWNER
-- First, create a new enum type with all values
CREATE TYPE access_type_new AS ENUM ('READ', 'WRITE', 'ADMIN', 'OWNER');

-- Update the table to use the new type
ALTER TABLE access_mappings 
  ALTER COLUMN access_type TYPE access_type_new 
  USING (access_type::text::access_type_new);

-- Drop the old type
DROP TYPE access_type;

-- Rename the new type to the original name
ALTER TYPE access_type_new RENAME TO access_type;
