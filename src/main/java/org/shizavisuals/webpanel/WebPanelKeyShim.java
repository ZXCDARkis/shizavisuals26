package org.shizavisuals.webpanel;

import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.browser.CefFrame;
import org.lwjgl.glfw.GLFW;

public final class WebPanelKeyShim {
    private WebPanelKeyShim() {
    }

    private static final String SCRIPT = """
            (function(op, shift) {
              var a = document.activeElement;
              var field = a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA')
                          && typeof a.selectionStart === 'number';
              if (field) {
                var v = a.value, s = a.selectionStart, e = a.selectionEnd, nv = v, ns = s, ne = e;
                if (op === 'back') {
                  if (s !== e) { nv = v.slice(0, s) + v.slice(e); ns = ne = s; }
                  else if (s > 0) { nv = v.slice(0, s - 1) + v.slice(s); ns = ne = s - 1; }
                } else if (op === 'del') {
                  if (s !== e) { nv = v.slice(0, s) + v.slice(e); ns = ne = s; }
                  else if (s < v.length) { nv = v.slice(0, s) + v.slice(s + 1); ns = ne = s; }
                } else if (op === 'left') {
                  ns = ne = Math.max(0, (s === e ? s - 1 : Math.min(s, e)));
                  if (shift) { ns = Math.max(0, s - 1); ne = e; }
                } else if (op === 'right') {
                  ns = ne = Math.min(v.length, (s === e ? s + 1 : Math.max(s, e)));
                  if (shift) { ns = s; ne = Math.min(v.length, e + 1); }
                } else if (op === 'home') {
                  ns = 0; ne = shift ? e : 0;
                } else if (op === 'end') {
                  ns = shift ? s : v.length; ne = v.length;
                } else if (op === 'enter') {
                  if (a.tagName === 'TEXTAREA') {
                    nv = v.slice(0, s) + '\\n' + v.slice(e); ns = ne = s + 1;
                  } else if (a.form) {
                    if (a.form.requestSubmit) { a.form.requestSubmit(); } else { a.form.submit(); }
                    return;
                  } else { return; }
                } else { return; }
                if (nv !== v) {
                  var proto = a.tagName === 'TEXTAREA'
                              ? window.HTMLTextAreaElement.prototype
                              : window.HTMLInputElement.prototype;
                  var d = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (d && d.set) { d.set.call(a, nv); } else { a.value = nv; }
                  a.dispatchEvent(new Event('input', { bubbles: true }));
                }
                a.selectionStart = ns; a.selectionEnd = ne;
                return;
              }
              if (a && a.isContentEditable) {
                var sel = window.getSelection();
                if (op === 'back') { document.execCommand('delete'); }
                else if (op === 'del') { document.execCommand('forwardDelete'); }
                else if (op === 'enter') { document.execCommand('insertLineBreak'); }
                else if (sel && sel.modify) {
                  var how = shift ? 'extend' : 'move';
                  if (op === 'left') { sel.modify(how, 'backward', 'character'); }
                  else if (op === 'right') { sel.modify(how, 'forward', 'character'); }
                  else if (op === 'home') { sel.modify(how, 'backward', 'lineboundary'); }
                  else if (op === 'end') { sel.modify(how, 'forward', 'lineboundary'); }
                }
                return;
              }
              if (op === 'left') { window.scrollBy(-60, 0); }
              else if (op === 'right') { window.scrollBy(60, 0); }
              else if (op === 'home') { window.scrollTo(0, 0); }
              else if (op === 'end') { window.scrollTo(0, document.body.scrollHeight); }
            })('%s', %s);""";

    public static boolean handles(int glfwKey) {
        return op(glfwKey) != null;
    }

    private static String op(int glfwKey) {
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_BACKSPACE -> "back";
            case GLFW.GLFW_KEY_DELETE -> "del";
            case GLFW.GLFW_KEY_LEFT -> "left";
            case GLFW.GLFW_KEY_RIGHT -> "right";
            case GLFW.GLFW_KEY_HOME -> "home";
            case GLFW.GLFW_KEY_END -> "end";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "enter";
            default -> null;
        };
    }

    public static boolean handle(MCEFBrowser browser, int glfwKey, int modifiers) {
        String op = op(glfwKey);
        if (op == null) {
            return false;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        try {
            CefFrame frame = browser.getCefBrowser().getFocusedFrame();
            if (frame == null) {
                frame = browser.getCefBrowser().getMainFrame();
            }
            if (frame == null) {
                return false;
            }
            frame.executeJavaScript(String.format(SCRIPT, op, shift), frame.getURL(), 0);
        } catch (Throwable ignored) {
        }
        return true;
    }
}
