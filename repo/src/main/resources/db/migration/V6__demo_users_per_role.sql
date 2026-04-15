-- V6: Demo users for every non-admin role so reviewers can exercise the
-- RBAC matrix without manually creating accounts. All demo users share the
-- same dev-only password as the admin (`admin123`) — the BCrypt hash is the
-- exact one already shipped in V1__baseline.sql for the admin row, so no
-- new password material is introduced here.
--
-- These rows are dev-only seed data. Production deployments must rotate
-- credentials per the README's "Change immediately in any non-development
-- environment" note.
--
-- Idempotent: ON CONFLICT DO NOTHING on the unique username column makes
-- this migration safe to re-run if a username happens to be pre-occupied
-- in a non-pristine environment.

INSERT INTO users (username, password_hash, display_name, status) VALUES
    ('editor',     '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Content Editor', 'ACTIVE'),
    ('moderator',  '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Moderator',      'ACTIVE'),
    ('clerk',      '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Billing Clerk',  'ACTIVE'),
    ('dispatcher', '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Dispatcher',     'ACTIVE'),
    ('driver',     '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Driver',         'ACTIVE'),
    ('auditor',    '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'Demo Auditor',        'ACTIVE')
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
    ((SELECT id FROM users WHERE username = 'editor'),     'CONTENT_EDITOR'),
    ((SELECT id FROM users WHERE username = 'moderator'),  'MODERATOR'),
    ((SELECT id FROM users WHERE username = 'clerk'),      'BILLING_CLERK'),
    ((SELECT id FROM users WHERE username = 'dispatcher'), 'DISPATCHER'),
    ((SELECT id FROM users WHERE username = 'driver'),     'DRIVER'),
    ((SELECT id FROM users WHERE username = 'auditor'),    'AUDITOR')
ON CONFLICT (user_id, role) DO NOTHING;
