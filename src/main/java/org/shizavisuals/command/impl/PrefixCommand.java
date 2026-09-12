package org.shizavisuals.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.utils.text.ChatUtil;

public final class PrefixCommand extends ClientCommand {
    private static final int MAX_LENGTH = 5;

    private final CommandManager manager;

    public PrefixCommand(CommandManager manager) {
        super("prefix", "Shows or changes the command prefix", ":pencil2:");
        this.manager = manager;
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.info("Current prefix  •  " + manager.getPrefix());
            return 1;
        });
        builder.then(RequiredArgumentBuilder.argument("prefix", StringArgumentType.word()).executes(context -> {
            String prefix = StringArgumentType.getString(context, "prefix");
            if (prefix.length() > MAX_LENGTH) {
                ChatUtil.error("Prefix is too long  •  Maximum " + MAX_LENGTH + " characters");
                return 0;
            }
            manager.setPrefix(prefix);
            ChatUtil.success("Prefix changed to  •  " + manager.getPrefix());
            return 1;
        }));
    }
}
