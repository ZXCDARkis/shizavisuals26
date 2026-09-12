package org.shizavisuals.webpanel;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WebPanelUrls {

    private WebPanelUrls() {
    }

    public static final String HOME = "https://www.google.com";

    public static String search(String query) {
        return "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    public static String normalize(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return HOME;
        }
        if (text.matches("(?i)^[a-z][a-z0-9+.-]*://.*") || text.startsWith("about:")
                || text.startsWith("data:")) {
            return text;
        }
        boolean host = !text.contains(" ")
                && (text.matches("^[\\p{L}\\d._-]+\\.[\\p{L}]{2,}(/.*)?$")
                || text.equals("localhost") || text.startsWith("localhost/")
                || text.startsWith("localhost:"));
        return host ? "https://" + text : search(text);
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = new URI(url).getHost();
            if (host != null && !host.isBlank()) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Throwable ignored) {
        }
        String text = url;
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            text = text.substring(scheme + 3);
        }
        int slash = text.indexOf('/');
        return slash > 0 ? text.substring(0, slash) : text;
    }
}
