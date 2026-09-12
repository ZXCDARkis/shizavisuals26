package org.shizavisuals.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class AsmEventInvokerFactory {
    private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();
    private static final MethodType INVOKER_TYPE = MethodType.methodType(void.class, Object.class, Event.class);

    private AsmEventInvokerFactory() {
    }

    public static EventInvoker compile(Method method) {
        try {
            MethodHandle handle = MethodHandles.privateLookupIn(method.getDeclaringClass(), ROOT_LOOKUP).unreflect(method);
            if (Modifier.isStatic(method.getModifiers())) {
                handle = MethodHandles.dropArguments(handle, 0, Object.class);
            }
            MethodHandle invoker = handle.asType(INVOKER_TYPE);
            return (target, event) -> {
                try {
                    invoker.invokeExact(target, event);
                } catch (Throwable throwable) {
                    throw new EventInvocationException(throwable);
                }
            };
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to compile event invoker for " + method, throwable);
        }
    }

    private static final class EventInvocationException extends RuntimeException {
        private EventInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
