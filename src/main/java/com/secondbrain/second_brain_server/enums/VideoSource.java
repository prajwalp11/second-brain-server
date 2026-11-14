package com.secondbrain.second_brain_server.enums;

/** Provenance of a resolved video. */
public enum VideoSource {
    DIRECT,          // a specific video chosen from search + ranking
    PLAYLIST_ITEM,   // a lesson pulled from a playlist (Stage 2 continuity)
    SEARCH_FALLBACK  // no good video found -> a YouTube search-results link
}
