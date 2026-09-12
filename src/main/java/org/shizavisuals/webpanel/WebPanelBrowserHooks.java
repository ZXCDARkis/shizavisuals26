package org.shizavisuals.webpanel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefCommandLine;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefJSDialogCallback;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDialogHandler;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.misc.BoolRef;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebPanelBrowserHooks {

    private WebPanelBrowserHooks() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-WebPanel");

    private static final int CHROME_MAJOR = 146;

    private static final boolean DROP_DISABLE_WEB_SECURITY = true;

    private static final String USER_AGENT = buildUserAgent();

    private static volatile boolean appHandlerInstalled;
    private static volatile boolean clientHooked;

    private static final Map<CefBrowser, Owner> OWNERS = new ConcurrentHashMap<>();

    public interface Owner {
        void onAddress(String url);

        void onTitle(String title);

        void onPopup(String url);
    }

    public static void installAppHandler() {
        if (appHandlerInstalled) {
            return;
        }
        appHandlerInstalled = true;
        try {
            CefApp.addAppHandler(new CefAppHandlerAdapter(new String[0]) {
                @Override
                public void onBeforeCommandLineProcessing(String processType, CefCommandLine commandLine) {
                    if (processType != null && !processType.isEmpty()) {
                        return;
                    }
                    patchCommandLine(commandLine);
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: не удалось настроить командную строку CEF: {}", t.toString());
        }
    }

    private static void patchCommandLine(CefCommandLine commandLine) {
        try {
            if (DROP_DISABLE_WEB_SECURITY && commandLine.hasSwitch("disable-web-security")) {
                dropSwitch(commandLine, "disable-web-security");
            }
            commandLine.appendSwitchWithValue("user-agent", USER_AGENT);
            LOGGER.info("WebPanel: User-Agent = {}", USER_AGENT);
        } catch (Throwable t) {
            LOGGER.error("WebPanel: не удалось поправить командную строку CEF", t);
        }
    }

    private static void dropSwitch(CefCommandLine commandLine, String name) {
        Map<String, String> switches = new LinkedHashMap<>(commandLine.getSwitches());
        List<String> arguments = new ArrayList<>(commandLine.getArguments());
        String program = commandLine.getProgram();
        if (switches.remove(name) == null) {
            return;
        }
        if (switches.isEmpty() && arguments.isEmpty()) {
            LOGGER.warn("WebPanel: командная строка CEF прочиталась пустой, --{} оставлен", name);
            return;
        }

        commandLine.reset();
        if (program != null && !program.isEmpty()) {
            commandLine.setProgram(program);
        }
        for (Map.Entry<String, String> entry : switches.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                commandLine.appendSwitch(entry.getKey());
            } else {
                commandLine.appendSwitchWithValue(entry.getKey(), value);
            }
        }
        for (String argument : arguments) {
            commandLine.appendArgument(argument);
        }
        LOGGER.info("WebPanel: ключ --{} снят (мешает проверкам Cloudflare)", name);
    }

    private static String buildUserAgent() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String platform;
        if (os.contains("mac")) {
            platform = "Macintosh; Intel Mac OS X 10_15_7";
        } else if (os.contains("win")) {
            platform = "Windows NT 10.0; Win64; x64";
        } else {
            platform = "X11; Linux x86_64";
        }
        return "Mozilla/5.0 (" + platform + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + CHROME_MAJOR + ".0.0.0 Safari/537.36";
    }

    public static void register(MCEFBrowser browser, Owner owner) {
        CefBrowser cef;
        try {
            cef = browser.getCefBrowser();
        } catch (Throwable t) {
            return;
        }
        if (cef == null) {
            return;
        }
        OWNERS.put(cef, owner);
        hookClient(cef);
    }

    public static void unregister(@Nullable MCEFBrowser browser) {
        if (browser == null) {
            return;
        }
        try {
            OWNERS.remove(browser.getCefBrowser());
        } catch (Throwable ignored) {
        }
    }

    private static void hookClient(CefBrowser browser) {
        if (clientHooked) {
            return;
        }
        CefClient client;
        try {
            client = browser.getClient();
        } catch (Throwable t) {
            return;
        }
        if (client == null) {
            return;
        }
        clientHooked = true;

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                try {
                    if (frame != null && !frame.isMain()) {
                        return;
                    }
                    Owner owner = OWNERS.get(browser);
                    if (owner != null && url != null) {
                        owner.onAddress(url);
                    }
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                try {
                    Owner owner = OWNERS.get(browser);
                    if (owner != null) {
                        owner.onTitle(title);
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame,
                                          String targetUrl, String targetFrameName) {
                try {
                    Owner owner = OWNERS.get(browser);
                    if (owner == null || targetUrl == null || targetUrl.isEmpty()) {
                        return owner != null;
                    }
                    owner.onPopup(targetUrl);
                } catch (Throwable ignored) {
                }
                return true;
            }
        });

        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                                             CefContextMenuParams params, CefMenuModel model) {
                try {
                    if (model != null) {
                        model.clear();
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        client.addJSDialogHandler(new CefJSDialogHandlerAdapter() {
            @Override
            public boolean onJSDialog(CefBrowser browser, String originUrl,
                                      CefJSDialogHandler.JSDialogType dialogType,
                                      String messageText, String defaultPromptText,
                                      CefJSDialogCallback callback, BoolRef suppressMessage) {
                try {
                    if (suppressMessage != null) {
                        suppressMessage.set(true);
                    }
                } catch (Throwable ignored) {
                }
                return false;
            }

            @Override
            public boolean onBeforeUnloadDialog(CefBrowser browser, String messageText,
                                                 boolean isReload, CefJSDialogCallback callback) {
                try {
                    if (callback != null) {
                        callback.Continue(true, "");
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
        });

        client.addDialogHandler(new CefDialogHandler() {
            @Override
            public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title,
                                        String defaultFilePath, Vector<String> acceptFilters,
                                        Vector<String> acceptExtensions,
                                        Vector<String> acceptDescriptions,
                                        CefFileDialogCallback callback) {
                try {
                    if (callback != null) {
                        callback.Cancel();
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
        });

        client.addDownloadHandler(new CefDownloadHandlerAdapter() {
            @Override
            public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
                                             String suggestedName,
                                             CefBeforeDownloadCallback callback) {
                return false;
            }
        });
    }
}
