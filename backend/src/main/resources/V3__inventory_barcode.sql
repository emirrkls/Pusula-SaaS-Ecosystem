-- =====================================================
--  PUSULA SaaS MIGRATION: Barcode column for Inventory
--  Version: V3__inventory_barcode.sql
-- =====================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='inventory' AND column_name='barcode') THEN
        ALTER TABLE inventory ADD COLUMN barcode VARCHAR(100);
    END IF;
END $$;

-- Index for barcode lookup (used by iOS scanner)
CREATE INDEX IF NOT EXISTS idx_inventory_barcode_company
    ON inventory(barcode, company_id) WHERE barcode IS NOT NULL;
