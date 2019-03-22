ALTER TABLE notification_queue
  ALTER COLUMN template_id DROP NOT NULL,
  ADD COLUMN subject text,
  ADD COLUMN body text;