-- ============================================
-- EXTENSIONS
-- ============================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- ============================================
-- USERS
-- ============================================
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email VARCHAR(255) UNIQUE,
                       password VARCHAR(255),
                       first_name VARCHAR(255),
                       last_name VARCHAR(255),
                       ai_daily_limit INT DEFAULT 3,
                       ai_used_today INT DEFAULT 0,
                       created_at TIMESTAMP DEFAULT NOW(),
                       updated_at TIMESTAMP DEFAULT NOW()
);


-- ============================================
-- DOMAINS
-- ============================================
CREATE TABLE domains (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         user_id UUID NOT NULL,
                         domain_type VARCHAR(50),
                         custom_name VARCHAR(255),
                         skill_level VARCHAR(50),
                         status VARCHAR(50),
                         plan_description TEXT,
                         context VARCHAR(500),
                         weekly_schedule TEXT,
                         linked_resource_url VARCHAR(500),
                         linked_resource_title VARCHAR(255),
                         current_streak INT DEFAULT 0,
                         longest_streak INT DEFAULT 0,
                         last_log_date TIMESTAMP,
                         created_at TIMESTAMP DEFAULT NOW(),
                         updated_at TIMESTAMP DEFAULT NOW(),

                         CONSTRAINT fk_domains_user
                             FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


-- INDEXES
CREATE INDEX idx_domains_user ON domains(user_id);


-- ============================================
-- DOMAIN METRIC DEFINITIONS
-- ============================================
CREATE TABLE domain_metric_definitions (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                           domain_id UUID NOT NULL,
                                           metric_key VARCHAR(255),
                                           label VARCHAR(255),
                                           unit VARCHAR(100),
                                           is_tracked_per_session BOOLEAN DEFAULT FALSE,
                                           is_pr BOOLEAN DEFAULT FALSE,
                                           is_higher_better BOOLEAN DEFAULT TRUE,
                                           display_order INT,
                                           created_at TIMESTAMP DEFAULT NOW(),
                                           updated_at TIMESTAMP DEFAULT NOW(),

                                           CONSTRAINT fk_metric_domain
                                               FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_metric_defs_domain ON domain_metric_definitions(domain_id);


-- ============================================
-- SESSION LOGS
-- ============================================
CREATE TABLE session_logs (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id UUID NOT NULL,
                              domain_id UUID NOT NULL,
                              session_type VARCHAR(100),
                              log_date TIMESTAMP,
                              duration_minutes INT,
                              feel_score INT,
                              feel_label VARCHAR(50),
                              notes TEXT,
                              linked_reference_url VARCHAR(500),
                              ai_insight TEXT,
                              created_at TIMESTAMP DEFAULT NOW(),
                              updated_at TIMESTAMP DEFAULT NOW(),

                              CONSTRAINT fk_session_user
                                  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

                              CONSTRAINT fk_session_domain
                                  FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);


-- INDEXES
CREATE INDEX idx_session_log_domain_date
    ON session_logs(domain_id, log_date);

CREATE INDEX idx_session_log_user
    ON session_logs(user_id);


-- ============================================
-- SESSION METRIC VALUES
-- ============================================
CREATE TABLE session_metric_values (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       session_log_id UUID NOT NULL,
                                       metric_key VARCHAR(255),
                                       numeric_value DOUBLE PRECISION,
                                       unit VARCHAR(100),
                                       created_at TIMESTAMP DEFAULT NOW(),
                                       updated_at TIMESTAMP DEFAULT NOW(),

                                       CONSTRAINT fk_metric_session
                                           FOREIGN KEY (session_log_id) REFERENCES session_logs(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_metric_values_session ON session_metric_values(session_log_id);


-- ============================================
-- MILESTONES
-- ============================================
CREATE TABLE milestones (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            domain_id UUID NOT NULL,
                            label VARCHAR(255),
                            metric_key VARCHAR(255),
                            target_value DOUBLE PRECISION,
                            current_value DOUBLE PRECISION,
                            unit VARCHAR(100),
                            status VARCHAR(50),
                            deadline DATE,
                            completed_at TIMESTAMP,
                            ai_generated BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT NOW(),
                            updated_at TIMESTAMP DEFAULT NOW(),

                            CONSTRAINT fk_milestone_domain
                                FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_milestones_domain ON milestones(domain_id);


-- ============================================
-- PERSONAL RECORDS
-- ============================================
CREATE TABLE personal_records (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id UUID NOT NULL,
                                  domain_id UUID NOT NULL,
                                  session_log_id UUID,
                                  metric_key VARCHAR(255),
                                  value DOUBLE PRECISION,
                                  unit VARCHAR(100),
                                  achieved_at TIMESTAMP,
                                  created_at TIMESTAMP DEFAULT NOW(),
                                  updated_at TIMESTAMP DEFAULT NOW(),

                                  CONSTRAINT fk_pr_user
                                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

                                  CONSTRAINT fk_pr_domain
                                      FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE,

                                  CONSTRAINT fk_pr_session
                                      FOREIGN KEY (session_log_id) REFERENCES session_logs(id) ON DELETE SET NULL
);

-- INDEXES
CREATE INDEX idx_personal_records_domain ON personal_records(domain_id, metric_key);


-- ============================================
-- TASKS
-- ============================================
CREATE TABLE tasks (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id UUID NOT NULL,
                       domain_id UUID,
                       title VARCHAR(255),
                       description TEXT,
                       status VARCHAR(50),
                       due_date TIMESTAMP,
                       completed_at TIMESTAMP,
                       progress INT DEFAULT 0,
                       ai_generated BOOLEAN DEFAULT FALSE,
                       created_at TIMESTAMP DEFAULT NOW(),
                       updated_at TIMESTAMP DEFAULT NOW(),

                       CONSTRAINT fk_task_user
                           FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

                       CONSTRAINT fk_task_domain
                           FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_tasks_user ON tasks(user_id);
CREATE INDEX idx_tasks_domain ON tasks(domain_id);


-- ============================================
-- AI CONVERSATIONS
-- ============================================
CREATE TABLE ai_conversations (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id UUID NOT NULL,
                                  preview VARCHAR(500),
                                  created_at TIMESTAMP DEFAULT NOW(),
                                  updated_at TIMESTAMP DEFAULT NOW(),

                                  CONSTRAINT fk_ai_conversation_user
                                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_ai_conversations_user ON ai_conversations(user_id);


-- ============================================
-- AI MESSAGES
-- ============================================
CREATE TABLE ai_messages (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             conversation_id UUID NOT NULL,
                             role VARCHAR(50),
                             content TEXT,
                             status VARCHAR(20),
                             error_message TEXT,
                             created_at TIMESTAMP DEFAULT NOW(),

                             CONSTRAINT fk_ai_message_conversation
                                 FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_ai_messages_conversation ON ai_messages(conversation_id);


-- ============================================
-- AI NUDGES
-- ============================================
CREATE TABLE ai_nudges (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id UUID NOT NULL,
                           domain_id UUID,
                           message TEXT,
                           nudge_type VARCHAR(50),
                           is_read BOOLEAN DEFAULT FALSE,
                           generated_at TIMESTAMP,
                           read_at TIMESTAMP,

                           CONSTRAINT fk_ai_nudge_user
                               FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

                           CONSTRAINT fk_ai_nudge_domain
                               FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

-- INDEXES
CREATE INDEX idx_ai_nudges_user ON ai_nudges(user_id);
-- ============================================
-- CUSTOM DOMAIN UNIQUE CONSTRAINT
-- Allows multiple CUSTOM domains per user, but each must have a unique custom_name
-- Different users can have the same custom_name
-- ============================================
CREATE UNIQUE INDEX uq_domain_user_custom_name
    ON domains (user_id, custom_name)
    WHERE domain_type = 'CUSTOM';


-- ============================================
-- ALTER: Add resource link fields to tasks
-- ============================================
ALTER TABLE tasks ADD COLUMN linked_resource_url VARCHAR(500);
ALTER TABLE tasks ADD COLUMN linked_resource_title VARCHAR(255);

-- ============================================
-- ALTER: Remove resource link fields from domains (moved to task level)
-- ============================================
ALTER TABLE domains DROP COLUMN IF EXISTS linked_resource_url;
ALTER TABLE domains DROP COLUMN IF EXISTS linked_resource_title;

-- ============================================
-- ALTER: Add per-user AI daily limit tracking
-- ============================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_daily_limit INT DEFAULT 3;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_used_today INT DEFAULT 0;


-- ============================================
-- JOB EXECUTION LOGS (scheduler/cron audit trail)
-- ============================================
CREATE TABLE IF NOT EXISTS job_execution_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    error_message TEXT,
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_logs_name_date ON job_execution_logs(job_name, started_at);


-- ============================================
-- ALTER: Add optional value bounds to metric definitions
-- Bounds are inferred (AI-generated or unit-based), never required from the user.
-- min_value/max_value NULL = no bound on that side (e.g. reps/weight can grow freely).
-- Percentage/score metrics get 0..100 so displayed values can't exceed 100%.
-- ============================================
ALTER TABLE domain_metric_definitions ADD COLUMN IF NOT EXISTS min_value DOUBLE PRECISION;
ALTER TABLE domain_metric_definitions ADD COLUMN IF NOT EXISTS max_value DOUBLE PRECISION;

-- Backfill: clamp existing percentage-style metrics to 0..100
UPDATE domain_metric_definitions
   SET min_value = 0, max_value = 100
 WHERE (min_value IS NULL AND max_value IS NULL)
   AND (TRIM(unit) = '%' OR LOWER(unit) LIKE '%percent%');


-- ============================================
-- ALTER: Add AI call outcome tracking to messages
-- status (SUCCESS/DEGRADED/FAILED) + error_message are set on the ASSISTANT row.
-- Lets us see when the AI (Gemini) is down without a separate audit table:
--   SELECT created_at, status, error_message FROM ai_messages
--   WHERE role = 'ASSISTANT' AND status <> 'SUCCESS' ORDER BY created_at DESC;
-- ============================================
ALTER TABLE ai_messages ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE ai_messages ADD COLUMN IF NOT EXISTS error_message TEXT;


-- ============================================
-- ALTER: Add optional user context to domains
-- Free-text intent/focus for generation, e.g. LANGUAGE -> "Spanish, conversational".
-- Optional; blank still produces a generic plan. Editable + used on regenerate.
-- ============================================
ALTER TABLE domains ADD COLUMN IF NOT EXISTS context VARCHAR(500);