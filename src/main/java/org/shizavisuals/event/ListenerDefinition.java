package org.shizavisuals.event;

record ListenerDefinition(
        Class<? extends Event> eventType,
        int priority,
        String methodName,
        EventInvoker invoker
) {
}
