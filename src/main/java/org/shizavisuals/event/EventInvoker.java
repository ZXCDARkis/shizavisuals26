package org.shizavisuals.event;

@FunctionalInterface
public interface EventInvoker {
    void invoke(Object target, Event event);
}
