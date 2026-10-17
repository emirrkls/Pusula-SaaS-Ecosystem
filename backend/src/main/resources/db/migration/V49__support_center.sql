-- Private support records are deliberately separate from service tickets and public uploads.
CREATE TABLE support_tickets (
 id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 subject VARCHAR(120) NOT NULL, category VARCHAR(16) NOT NULL,
 status VARCHAR(24) NOT NULL, priority VARCHAR(12) NOT NULL,
 platform VARCHAR(16) NOT NULL, app_version VARCHAR(32) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 user_read_at TIMESTAMP WITH TIME ZONE, admin_read_at TIMESTAMP WITH TIME ZONE,
 last_user_at TIMESTAMP WITH TIME ZONE, last_admin_at TIMESTAMP WITH TIME ZONE,
 closed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_support_owner ON support_tickets(company_id,user_id,updated_at DESC,id DESC);
CREATE INDEX ix_support_queue ON support_tickets(status,updated_at DESC,id DESC);
CREATE TABLE support_messages (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
 author_id BIGINT, staff BOOLEAN NOT NULL, internal_note BOOLEAN NOT NULL,
 body VARCHAR(4000) NOT NULL, client_key VARCHAR(36) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_support_message_actor_key UNIQUE(author_id,client_key)
);
CREATE INDEX ix_support_messages ON support_messages(ticket_id,id DESC);
CREATE TABLE support_attachments (
 id BIGSERIAL PRIMARY KEY, message_id BIGINT NOT NULL REFERENCES support_messages(id) ON DELETE CASCADE,
 file_name VARCHAR(64) NOT NULL, media_type VARCHAR(64) NOT NULL, content BYTEA NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE support_events (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
 actor_id BIGINT, event_type VARCHAR(32) NOT NULL, event_value VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_support_attachments_message ON support_attachments(message_id);
CREATE INDEX ix_support_attachments_expiry ON support_attachments(created_at);
CREATE TABLE support_mail_outbox (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
 message_id BIGINT NOT NULL UNIQUE REFERENCES support_messages(id) ON DELETE CASCADE,
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
 delivered_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_support_mail_due ON support_mail_outbox(next_attempt_at,id) WHERE delivered_at IS NULL AND attempts < 8;
