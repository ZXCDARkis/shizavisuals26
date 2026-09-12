package org.shizavisuals.event;

record ListenerRegistration(
        Class<? extends Event> eventType,
        RegisteredListener listener
) {
}
