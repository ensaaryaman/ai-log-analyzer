-- ============================================================================
-- V2 — analysis.raw_response sütununu jsonb'den text'e çevir.
-- Neden: Modelin HAM yanıtı her zaman geçerli JSON olmayabilir (markdown ```json çiti,
-- kısmi çıktı vb.). Ham yanıtı hata ayıklama için oldukça saklamak istiyoruz; bu yüzden
-- katı jsonb yerine esnek text kullanıyoruz. (evidence_lines jsonb olarak kalır — o her zaman geçerli.)
-- ============================================================================

ALTER TABLE analysis
    ALTER COLUMN raw_response TYPE text USING raw_response::text;
