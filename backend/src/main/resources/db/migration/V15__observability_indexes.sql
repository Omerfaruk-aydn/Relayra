CREATE INDEX idx_member_roles_member ON member_roles(community_member_id);
CREATE INDEX idx_messages_author_client ON messages(author_id, client_message_id);
CREATE INDEX idx_messages_reply ON messages(reply_to_message_id);
