CREATE TABLE sheets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    max_length INTEGER NOT NULL DEFAULT 100,
    max_breadth INTEGER NOT NULL DEFAULT 100,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sheets_user_id ON sheets (user_id);

CREATE TRIGGER update_sheets_updated_at
BEFORE UPDATE ON sheets
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
