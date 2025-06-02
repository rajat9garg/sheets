CREATE TYPE access_type AS ENUM ('READ', 'WRITE', 'ADMIN');

CREATE TABLE access_mappings (
    id BIGSERIAL PRIMARY KEY,
    sheet_id BIGINT NOT NULL REFERENCES sheets(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_type access_type NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sheet_id, user_id)
);

CREATE INDEX idx_access_mappings_sheet_id ON access_mappings (sheet_id);
CREATE INDEX idx_access_mappings_user_id ON access_mappings (user_id);

CREATE TRIGGER update_access_mappings_updated_at
BEFORE UPDATE ON access_mappings
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
