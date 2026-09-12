package org.shizavisuals.webpanel;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebPanelAwtGuard {

    private WebPanelAwtGuard() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-WebPanel");

    private static final int LOG_LIMIT = 5;

    private static volatile boolean installed;
    private static volatile long lastArm;
    private static final Thread.UncaughtExceptionHandler HANDLER =
            (thread, t) -> report(t);
    private static volatile Thread.UncaughtExceptionHandler defaultHandler;
    private static final Set<String> seen = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final AtomicInteger swallowed = new AtomicInteger();
    private static volatile EventQueue pushedQueue;

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        installDefaultHandler();
        rearm();
        try {
            EventQueue.invokeLater(() -> {
                try {
                    EventQueue q = new EventQueue() {
                        @Override
                        protected void dispatchEvent(AWTEvent event) {
                            try {
                                super.dispatchEvent(event);
                            } catch (Throwable t) {
                                report(t);
                            }
                        }
                    };
                    pushedQueue = q;
                    Toolkit.getDefaultToolkit().getSystemEventQueue().push(q);
                } catch (Throwable t) {
                    LOGGER.error("WebPanel: не удалось подменить AWT EventQueue", t);
                }
            });
        } catch (Throwable t) {

            LOGGER.error("WebPanel: AWT недоступен", t);
        }
    }

    private static void installDefaultHandler() {
        try {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            if (previous == defaultHandler && defaultHandler != null) {
                return;
            }
            Thread.UncaughtExceptionHandler chained = (thread, t) -> {
                if (thread != null && thread.getName().startsWith("AWT-EventQueue")) {
                    report(t);
                    return;
                }
                if (previous != null) {
                    previous.uncaughtException(thread, t);
                } else {
                    LOGGER.error("Необработанное исключение в потоке {}",
                            thread == null ? "?" : thread.getName(), t);
                }
            };
            defaultHandler = chained;
            Thread.setDefaultUncaughtExceptionHandler(chained);
        } catch (Throwable t) {
            LOGGER.error("WebPanel: не удалось поставить обработчик исключений", t);
        }
    }

    public static void rearm() {
        long now = System.currentTimeMillis();
        if (now - lastArm < 1000L) {
            return;
        }
        lastArm = now;
        try {
            EventQueue.invokeLater(() -> {
                Thread self = Thread.currentThread();
                if (self.getUncaughtExceptionHandler() != HANDLER) {
                    self.setUncaughtExceptionHandler(HANDLER);
                }
            });
        } catch (Throwable ignored) {

        }
    }

    private static void report(Throwable t) {
        int total = swallowed.incrementAndGet();
        String key = signature(t);
        if (seen.size() < LOG_LIMIT && seen.add(key)) {
            LOGGER.error("WebPanel: исключение в AWT-потоке CEF (проглочено, поток жив)", t);
        } else if (total % 500 == 0) {
            LOGGER.warn("WebPanel: исключений в AWT-потоке CEF уже {}", total);
        }
    }

    private static String signature(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        StackTraceElement[] stack = t.getStackTrace();
        for (int i = 0; i < Math.min(4, stack.length); i++) {
            sb.append('|').append(stack[i]);
        }
        return sb.toString();
    }

    public static void shutdown() {
        try {
            EventQueue q = pushedQueue;
            if (q != null) {
                pushedQueue = null;
                try {
                    java.lang.reflect.Method m = EventQueue.class.getDeclaredMethod("pop");
                    m.setAccessible(true);
                    m.invoke(Toolkit.getDefaultToolkit().getSystemEventQueue());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            EventQueue.invokeLater(() -> {});
        } catch (Throwable ignored) {
        }
    }
}
