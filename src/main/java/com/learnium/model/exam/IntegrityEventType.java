package com.learnium.model.exam;

/** Student-side integrity signals captured by the player (M5 Part 5). Flags only — never an auto-fail. */
public enum IntegrityEventType {
    TAB_HIDDEN,
    FOCUS_LOST,
    MOUSE_LEFT,
    FULLSCREEN_EXIT,
    CAMERA_LOST
}
