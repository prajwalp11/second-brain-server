package com.secondbrain.second_brain_server.enums;

/**
 * Outcome status of an AI assistant message (one AI call).
 * SUCCESS  — Gemini responded and the reply parsed correctly.
 * DEGRADED — Gemini responded but the JSON did not parse (raw text used as fallback).
 * FAILED   — the Gemini call itself failed (outage); fallback text stored.
 */
public enum MessageStatus {
    SUCCESS,
    DEGRADED,
    FAILED
}
