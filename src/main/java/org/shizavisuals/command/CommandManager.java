package org.shizavisuals.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import org.shizavisuals.command.impl.BindCommand;
import org.shizavisuals.command.impl.ConfigCommand;
import org.shizavisuals.command.impl.FriendCommand;
import org.shizavisuals.command.impl.GpsCommand;
import org.shizavisuals.command.impl.HelpCommand;
import org.shizavisuals.command.impl.MacroCommand;
import org.shizavisuals.command.impl.PrefixCommand;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.KeyboardInputEvent;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.utils.text.ChatUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandManager {
    public static final CommandManager INSTANCE = new CommandManager();
    public static final String DEFAULT_PREFIX = ".";

    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
    private final List<ClientCommand> commands = new ArrayList<>();
    private final Map<String, Macro> macros = new LinkedHashMap<>();
    private final CommandStore store = new CommandStore();
    private String prefix = DEFAULT_PREFIX;
    private boolean initialized;

    private CommandManager() {
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        List.of(
                new HelpCommand(this),
                new PrefixCommand(this),
                new BindCommand(),
                new FriendCommand(),
                new MacroCommand(this),
                new GpsCommand(),
                new ConfigCommand()
        ).forEach(this::register);

        store.load(this);
        initialized = true;
    }

    public List<ClientCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public CommandDispatcher<Object> getDispatcher() {
        return dispatcher;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix.trim();
        store.save(this);
    }

    public boolean handleChat(String message) {
        if (!initialized || message == null || !message.startsWith(prefix)) {
            return false;
        }

        String input = message.substring(prefix.length()).trim();
        if (input.isEmpty()) {
            return false;
        }

        try {
            dispatcher.execute(input, new Object());
        } catch (CommandSyntaxException exception) {
            String detail = exception.getRawMessage() == null ? null : exception.getRawMessage().getString();
            ChatUtil.error(detail == null || detail.isBlank()
                    ? "Unknown command or syntax  •  Try " + prefix + "help"
                    : detail + "  •  Try " + prefix + "help");
        } catch (Exception exception) {
            ChatUtil.error("Command failed  •  " + exception.getMessage());
        }
        return true;
    }

    public Collection<Macro> getMacros() {
        return Collections.unmodifiableCollection(macros.values());
    }

    public Macro getMacro(String name) {
        return macros.get(normalize(name));
    }

    public void putMacro(Macro macro) {
        macros.put(normalize(macro.name()), macro);
        store.save(this);
    }

    public boolean removeMacro(String name) {
        boolean removed = macros.remove(normalize(name)) != null;
        if (removed) {
            store.save(this);
        }
        return removed;
    }

    void loadState(String prefix, List<Macro> loadedMacros) {
        this.prefix = prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix;
        macros.clear();
        for (Macro macro : loadedMacros) {
            macros.put(normalize(macro.name()), macro);
        }
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            dispatchMacros(BindSetting.key(event.getKey()));
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            dispatchMacros(BindSetting.mouse(event.getButton()));
        }
    }

    private void dispatchMacros(int bindCode) {
        Minecraft mc = MinecraftContext.mc;
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }
        for (Macro macro : macros.values()) {
            if (macro.bindCode() == bindCode) {
                send(mc, macro.text());
            }
        }
    }

    private void send(Minecraft mc, String text) {
        if (handleChat(text)) {
            return;
        }
        if (text.startsWith("/")) {
            mc.player.connection.sendCommand(text.substring(1));
        } else {
            mc.player.connection.sendChat(text);
        }
    }

    private void register(ClientCommand command) {
        registerLiteral(command.name(), command);
        for (String alias : command.aliases()) {
            registerLiteral(alias, command);
        }
        commands.add(command);
        EventManager.subscribe(command);
    }

    private void registerLiteral(String literal, ClientCommand command) {
        LiteralArgumentBuilder<Object> builder = LiteralArgumentBuilder.literal(literal);
        command.build(builder);
        dispatcher.register(builder);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
