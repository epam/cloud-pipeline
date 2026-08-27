-- Create Dead Letter Queue table for failed notifications
CREATE TABLE IF NOT EXISTS pipeline.notification_queue_dlq (
   id BIGSERIAL PRIMARY KEY,
   subject TEXT,
   body TEXT,
   template_id BIGINT,
   to_user_id BIGINT,
   user_ids text,
   template_parameters TEXT,
   reason TEXT,
   created_date TIMESTAMP DEFAULT NOW() NOT NULL,
   CONSTRAINT template_id_dlq_fkey FOREIGN KEY (template_id) REFERENCES pipeline.notification_template(id) ON DELETE SET NULL
);
