package org.shizavisuals.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.utils.text.ChatUtil;

public final class HelpCommand extends ClientCommand {
    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        super("help", "Shows all available commands", ":question:");
        this.manager = manager;
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.header("ShizaVisuals commands");
            for (ClientCommand command : manager.getCommands()) {
                ChatUtil.entry(command.emoji(), manager.getPrefix() + command.name(), command.description());
            }
            return 1;
        });
    }
}
