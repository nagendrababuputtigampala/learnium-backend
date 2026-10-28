-- V6: content tenancy invariants (independent review follow-up, finding 2).
--
-- V5 closed the roster and exam cross-centre links but left the content chain
-- open: a question could reference another centre's material, and a material
-- could name another centre's user as its uploader. Both foreign links are
-- traversable from an otherwise correctly centre-scoped query through the
-- entity graph, so the same database-level enforcement is applied here.
--
-- As in V5, the original single-column foreign keys from V3 are KEPT; the
-- composite keys are the tenancy enforcement and the redundancy is harmless.

-- 1) Parent unique key on (centre_id, material_id) — the target of the
-- composite FK from questions. (user_account already gained its
-- (centre_id, user_id) unique key in V5.)
ALTER TABLE materials
    ADD CONSTRAINT uq_materials_centre_material UNIQUE (centre_id, material_id);

-- 2) Composite foreign keys: a content row's centre must match its parent's.
-- questions.material_id is nullable (manual questions have no material); a
-- NULL component passes the composite check, a present one must be same-centre.
ALTER TABLE questions
    ADD CONSTRAINT fk_questions_centre_material
        FOREIGN KEY (centre_id, material_id) REFERENCES materials (centre_id, material_id);
ALTER TABLE materials
    ADD CONSTRAINT fk_materials_centre_uploader
        FOREIGN KEY (centre_id, uploaded_by) REFERENCES user_account (centre_id, user_id);
