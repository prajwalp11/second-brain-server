-- V1__create_initial_schema.sql

-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Create domains table
CREATE TABLE domains (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    user_id UUID NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create domain_metric_definitions table
CREATE TABLE domain_metric_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    unit VARCHAR(255),
    description TEXT,
    domain_id UUID,
    user_id UUID NOT NULL,
    FOREIGN KEY (domain_id) REFERENCES domains (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create personal_records table
CREATE TABLE personal_records (
    id UUID PRIMARY KEY,
    record_date DATE NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    notes TEXT,
    user_id UUID NOT NULL,
    domain_metric_definition_id UUID,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (domain_metric_definition_id) REFERENCES domain_metric_definitions (id)
);

-- Create tasks table
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date DATE,
    priority VARCHAR(50), -- Assuming ENUM will be mapped to VARCHAR
    status VARCHAR(50),   -- Assuming ENUM will be mapped to VARCHAR
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create milestones table
CREATE TABLE milestones (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    target_date DATE,
    completed_date DATE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create notification_preferences table
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    type VARCHAR(255) NOT NULL, -- Assuming ENUM will be mapped to VARCHAR
    enabled BOOLEAN NOT NULL,
    user_id UUID NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create ai_conversations table
CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    title VARCHAR(255),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create ai_messages table
CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    role VARCHAR(50) NOT NULL, -- Assuming ENUM will be mapped to VARCHAR
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    ai_conversation_id UUID NOT NULL,
    FOREIGN KEY (ai_conversation_id) REFERENCES ai_conversations (id)
);

-- Create ai_nudges table
CREATE TABLE ai_nudges (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL,
    ai_conversation_id UUID,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (ai_conversation_id) REFERENCES ai_conversations (id)
);

-- Create session_logs table
CREATE TABLE session_logs (
    id UUID PRIMARY KEY,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_seconds BIGINT,
    notes TEXT,
    user_id UUID NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Create session_metric_values table
CREATE TABLE session_metric_values (
    id UUID PRIMARY KEY,
    value DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    session_log_id UUID NOT NULL,
    domain_metric_definition_id UUID,
    FOREIGN KEY (session_log_id) REFERENCES session_logs (id),
    FOREIGN KEY (domain_metric_definition_id) REFERENCES domain_metric_definitions (id)
);
