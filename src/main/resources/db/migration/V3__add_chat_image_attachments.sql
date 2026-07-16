ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS attachment_key VARCHAR(100);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS attachment_content_type VARCHAR(100);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_messages_attachment_key
    ON chat_messages(attachment_key);
