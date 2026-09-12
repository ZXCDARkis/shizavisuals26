package org.shizavisuals.webpanel;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.Nullable;

public final class WebPanelTab implements WebPanelBrowserHooks.Owner {

    private final String startUrl;

    private volatile String url;

    private volatile String title = "";

    @Nullable
    private MCEFBrowser browser;
    private boolean failed;

    private int appliedWidth = -1;
    private int appliedHeight = -1;
    private double appliedZoom = Double.NaN;

    private boolean hidden;
    private boolean hiddenApplied;

    private int frameRate = 30;
    private int frameRateApplied = -1;

    private float contentAlpha;

    private float appear;

    public WebPanelTab(String url) {
        this.startUrl = WebPanelUrls.normalize(url);
        this.url = this.startUrl;
    }

    public String getUrl() {
        return this.url;
    }

    public String getStartUrl() {
        return this.startUrl;
    }

    public String label() {
        String name = this.title;
        if (name != null && !name.isBlank()) {
            return name;
        }
        String host = WebPanelUrls.host(this.url);
        return host.isEmpty() ? "…" : host;
    }

    @Nullable
    public MCEFBrowser getBrowser() {
        return this.browser;
    }

    public float getContentAlpha() {
        return this.contentAlpha;
    }

    public boolean hasContent() {
        MCEFBrowser current = this.browser;
        return current != null && current.getTextureView() != null;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setFocused(boolean focused) {
        MCEFBrowser current = this.browser;
        if (current == null) {
            return;
        }
        try {
            current.setFocus(focused);
        } catch (Throwable ignored) {
        }
    }

    public void load(String raw) {
        String target = WebPanelUrls.normalize(raw);
        this.url = target;
        withBrowser(cef -> cef.loadURL(target));
    }

    public void back() {
        withBrowser(cef -> {
            if (cef.canGoBack()) {
                cef.goBack();
            }
        });
    }

    public void forward() {
        withBrowser(cef -> {
            if (cef.canGoForward()) {
                cef.goForward();
            }
        });
    }

    public void reload() {
        withBrowser(CefBrowser::reload);
    }

    private void withBrowser(Consumer<CefBrowser> action) {
        MCEFBrowser current = this.browser;
        if (current == null) {
            return;
        }
        try {
            CefBrowser cef = current.getCefBrowser();
            if (cef != null) {
                action.accept(cef);
            }
        } catch (Throwable ignored) {
        }
    }

    public void tick(float deltaSeconds, int texWidth, int texHeight, double zoom) {
        if (this.browser == null && !this.failed) {
            create(texWidth, texHeight);
        }
        MCEFBrowser current = this.browser;
        if (current != null) {
            if (texWidth != this.appliedWidth || texHeight != this.appliedHeight) {
                try {
                    current.resize(texWidth, texHeight);
                    this.appliedWidth = texWidth;
                    this.appliedHeight = texHeight;
                } catch (Throwable ignored) {
                }
            }
            applyZoom(current, zoom);
            applyHidden(current);
        }

        this.appear = approach(this.appear, 1.0f, deltaSeconds / 0.16f);
        if (!this.hidden) {
            this.contentAlpha = approach(this.contentAlpha, hasContent() ? 1.0f : 0.0f,
                    deltaSeconds / 0.25f);
        }
    }

    private void create(int texWidth, int texHeight) {
        try {
            CompletableFuture<MCEFApi> future = MCEFApi.getInstanceFuture();
            if (!future.isDone()) {
                return;
            }
            if (future.isCompletedExceptionally()) {
                if (!this.failed) {
                    org.slf4j.LoggerFactory.getLogger("ShizaVisuals-WebPanel")
                            .error("WebPanel: инициализация MCEF завершилась ошибкой — браузер недоступен");
                }
                this.failed = true;
                return;
            }
            MCEFBrowser created = future.join().createBrowser(this.url, false);
            created.resize(texWidth, texHeight);
            this.appliedWidth = texWidth;
            this.appliedHeight = texHeight;
            WebPanelBrowserHooks.register(created, this);
            this.browser = created;
            org.slf4j.LoggerFactory.getLogger("ShizaVisuals-WebPanel")
                    .info("WebPanel: браузер создан ({})", this.url);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ShizaVisuals-WebPanel")
                    .error("WebPanel: не удалось создать браузер", t);
            this.failed = true;
        }
    }

    private void applyZoom(MCEFBrowser current, double zoom) {
        double level = Math.log(Math.max(0.25, zoom)) / Math.log(1.2);
        if (!Double.isNaN(this.appliedZoom) && Math.abs(level - this.appliedZoom) < 0.02) {
            return;
        }
        this.appliedZoom = level;
        withBrowser(cef -> cef.setZoomLevel(level));
    }

    private void applyHidden(MCEFBrowser current) {
        if (this.hidden == this.hiddenApplied && this.frameRate == this.frameRateApplied) {
            return;
        }
        boolean visibilityChanged = this.hidden != this.hiddenApplied;
        this.hiddenApplied = this.hidden;
        int fps = this.frameRate;
        this.frameRateApplied = fps;
        withBrowser(cef -> {
            if (visibilityChanged) {
                cef.setWindowVisibility(!this.hiddenApplied);
            }
            cef.setWindowlessFrameRate(fps);
        });
    }

    public void setFrameRate(int fps) {
        this.frameRate = Mth.clamp(fps, 1, 60);
    }

    private static float approach(float current, float target, float step) {
        return target > current ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    public void close() {
        MCEFBrowser current = this.browser;
        this.browser = null;
        if (current == null) {
            return;
        }
        WebPanelBrowserHooks.unregister(current);
        try {
            current.close();
        } catch (Throwable ignored) {
        }
    }

    public float easedAppear() {
        float inv = 1.0f - Mth.clamp(this.appear, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    @Override
    public void onAddress(String url) {
        this.url = url;
    }

    @Override
    public void onTitle(String title) {
        this.title = title == null ? "" : title;
    }

    @Override
    public void onPopup(String url) {
        load(url);
    }
}
