package com.secondbrain.second_brain_server.enums;

/** Position of a curriculum step in the user's ordered course. */
public enum StepStatus {
    LOCKED,   // not yet reached
    CURRENT,  // the step the user is on (its task(s) are active)
    DONE      // completed (immutable history)
}
