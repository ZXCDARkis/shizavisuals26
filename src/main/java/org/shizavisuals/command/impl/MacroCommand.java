package org.shizavisuals.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.command.KeyNames;
import org.shizavisuals.command.Macro;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.utils.text.ChatUtil;

public final class MacroCommand extends ClientCommand {
    private final CommandManager manager;

    public MacroCommand(CommandManager manager) {
        super("macro", "Sends a chat message or command on key press", ":speech_balloon:");
        this.manager = manager;
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.usage("macro add <name> <key> <text>  •  remove <name>  •  list");
            return 1;
        });
        builder.then(LiteralArgumentBuilder.literal("add")
                .then(RequiredArgumentBuilder.argument("name", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.argument("key", StringArgumentType.word())
                                .then(RequiredArgumentBuilder.argument("text", StringArgumentType.greedyString())
                                        .executes(this::executeAdd)))));
        builder.then(LiteralArgumentBuilder.literal("remove")
                .then(RequiredArgumentBuilder.argument("name", StringArgumentType.word())
                        .executes(this::executeRemove)));
        builder.then(LiteralArgumentBuilder.literal("list").executes(this::executeList));
    }

    private int executeAdd(CommandContext<Object> context) {
        String name = StringArgumentType.getString(context, "name");
        String keyName = StringArgumentType.getString(context, "key");
        String text = StringArgumentType.getString(context, "text");

        int bindCode = KeyNames.parse(keyName);
        if (bindCode == BindSetting.UNBOUND) {
            ChatUtil.error("Unknown key  •  " + keyName);
            return 0;
        }

        manager.putMacro(new Macro(name, bindCode, text));
        ChatUtil.success("Macro " + name + "  •  " + BindSetting.describe(bindCode) + "  •  " + text);
        return 1;
    }

    private int executeRemove(CommandContext<Object> context) {
        String name = StringArgumentType.getString(context, "name");
        if (manager.removeMacro(name)) {
            ChatUtil.success("Macro removed  •  " + name);
            return 1;
        }
        ChatUtil.error("Macro not found  •  " + name);
        return 0;
    }

    private int executeList(CommandContext<Object> context) {
        if (manager.getMacros().isEmpty()) {
            ChatUtil.info("No macros saved");
            return 1;
        }
        for (Macro macro : manager.getMacros()) {
            ChatUtil.entry(":speech_balloon:", macro.name(),
                    BindSetting.describe(macro.bindCode()) + "  •  " + macro.text());
        }
        return 1;
    }
}
