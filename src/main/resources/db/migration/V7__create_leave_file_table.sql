-- Create leave_file table
CREATE TABLE IF NOT EXISTS leave.leave_file (
    id BIGSERIAL PRIMARY KEY,
    leave_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_leave_file_leave FOREIGN KEY (leave_id) REFERENCES leave.leave(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_file_leave_id ON leave.leave_file(leave_id);
