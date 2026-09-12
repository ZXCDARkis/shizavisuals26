package org.shizavisuals.event;

import org.shizavisuals.event.events.game.AttackEvent;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.game.PlayerJumpEvent;
import org.shizavisuals.event.events.game.PlayerTickEvent;
import org.shizavisuals.event.events.lifecycle.ClientStartEvent;
import org.shizavisuals.event.events.lifecycle.DisconnectEvent;
import org.shizavisuals.event.events.lifecycle.ResourceReloadEvent;
import org.shizavisuals.event.events.lifecycle.ShutdownEvent;
import org.shizavisuals.event.events.lifecycle.WorldJoinEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.render.FinalGuiRenderEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.event.events.render.Render3DEvent;
import org.shizavisuals.event.events.screen.ScreenCloseEvent;
import org.shizavisuals.event.events.screen.ScreenKeyEvent;
import org.shizavisuals.event.events.screen.ScreenMouseButtonEvent;
import org.shizavisuals.event.events.screen.ScreenOpenEvent;
import org.shizavisuals.event.events.screen.ScreenRenderEvent;

public class Events {
    public static final ClientStartEvent CLIENT_START = new ClientStartEvent();
    public static final GameTickEvent GAME_TICK = new GameTickEvent();
    public static final PlayerTickEvent PLAYER_TICK = new PlayerTickEvent();
    public static final AttackEvent ATTACK = new AttackEvent();
    public static final PlayerJumpEvent PLAYER_JUMP = new PlayerJumpEvent();
    public static final WorldJoinEvent WORLD_JOIN = new WorldJoinEvent();
    public static final WorldLeaveEvent WORLD_LEAVE = new WorldLeaveEvent();
    public static final DisconnectEvent DISCONNECT = new DisconnectEvent();
    public static final ShutdownEvent SHUTDOWN = new ShutdownEvent();
    public static final ResourceReloadEvent RESOURCE_RELOAD = new ResourceReloadEvent();
    public static final ScreenOpenEvent SCREEN_OPEN = new ScreenOpenEvent();
    public static final ScreenCloseEvent SCREEN_CLOSE = new ScreenCloseEvent();
    public static final ScreenRenderEvent SCREEN_RENDER = new ScreenRenderEvent();
    public static final ScreenKeyEvent SCREEN_KEY = new ScreenKeyEvent();
    public static final ScreenMouseButtonEvent SCREEN_MOUSE_BUTTON = new ScreenMouseButtonEvent();
    public static final FinalGuiRenderEvent FINAL_GUI_RENDER = new FinalGuiRenderEvent();
    public static final Render2DEvent RENDER_2D = new Render2DEvent();
    public static final Render3DEvent RENDER_3D = new Render3DEvent();
}
