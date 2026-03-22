package com.secondbrain.second_brain_server.util;

public class ContextWindowUtil {

    public static String truncateToLimit(String text, int maxTokens) {
        // This is a very rough approximation. A real implementation would use a tokenizer.
        // Assuming average token length of 4 chars.
        int maxLength = maxTokens * 4;
        if (text.length() > maxLength) {
            return text.substring(0, maxLength);
        }
        return text;
    }
}
