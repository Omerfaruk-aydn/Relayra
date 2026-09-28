CREATE TABLE roles (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  name VARCHAR(64) NOT NULL,
  position INTEGER NOT NULL,
  color VARCHAR(16),
  managed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE(community_id, name),
  CHECK (char_length(name) BETWEEN 2 AND 64),
  CHECK (position >= 0)
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission VARCHAR(64) NOT NULL,
  PRIMARY KEY(role_id, permission)
);

CREATE TABLE member_roles (
  community_member_id UUID NOT NULL REFERENCES community_members(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY(community_member_id, role_id)
);

CREATE INDEX idx_roles_community_position ON roles(community_id, position);
CREATE INDEX idx_member_roles_role ON member_roles(role_id);

INSERT INTO roles (
  id, community_id, name, position, color, managed, created_at, updated_at
)
SELECT
  gen_random_uuid(), id, '@everyone', 0, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM communities;

INSERT INTO role_permissions (role_id, permission)
SELECT role.id, permission.permission
FROM roles role
CROSS JOIN (
  VALUES ('VIEW_CHANNEL'), ('SEND_MESSAGES'), ('ADD_REACTIONS'), ('ATTACH_FILES')
) AS permission(permission)
WHERE role.managed = TRUE AND role.name = '@everyone';
