-- S3-T1: SUPER_ADMIN global tenant support
-- Allow tenant-independent audit records for global operations.
ALTER TABLE IF EXISTS audit_logs
    ALTER COLUMN company_id DROP NOT NULL;
