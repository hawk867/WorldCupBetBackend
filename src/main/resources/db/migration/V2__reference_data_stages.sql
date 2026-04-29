-- =============================================================================
-- V2: Reference data - tournament stages
--
-- Seeds the seven tournament stages in the order used throughout the
-- competition. order_idx drives the default sort for UI listings and is the
-- basis for StageRepository.findAllByOrderByOrderIdxAsc().
-- =============================================================================

INSERT INTO stages (name, order_idx) VALUES
    ('GROUP_STAGE',   1),
    ('ROUND_OF_32',   2),
    ('ROUND_OF_16',   3),
    ('QUARTER_FINAL', 4),
    ('SEMI_FINAL',    5),
    ('THIRD_PLACE',   6),
    ('FINAL',         7);
