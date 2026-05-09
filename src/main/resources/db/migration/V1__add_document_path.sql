-- Add document_path column to leave table
ALTER TABLE leave.leave ADD COLUMN IF NOT EXISTS document_path VARCHAR(255);
