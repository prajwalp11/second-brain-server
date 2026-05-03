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
                         weekly_schedule VARCHAR(255),
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
                            deadline TIMESTAMP,
                            completed_at TIMESTAMP,
                            ai_generated BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT NOW(),
                            updated_at TIMESTAMP DEFAULT NOW(),

                            CONSTRAINT fk_milestone_domain
                                FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);


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


-- ============================================
-- AI MESSAGES
-- ============================================
CREATE TABLE ai_messages (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             conversation_id UUID NOT NULL,
                             role VARCHAR(50),
                             content TEXT,
                             created_at TIMESTAMP DEFAULT NOW(),

                             CONSTRAINT fk_ai_message_conversation
                                 FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);


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