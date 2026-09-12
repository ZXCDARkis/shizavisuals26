package org.shizavisuals.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.utils.FriendManager;
import org.shizavisuals.utils.text.ChatUtil;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FriendCommand extends ClientCommand {
    public FriendCommand() {
        super("friend", "Manages the friend list", ":busts_in_silhouette:");
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> showUsage());
        builder.then(LiteralArgumentBuilder.literal("add")
                .then(RequiredArgumentBuilder.argument("name", StringArgumentType.word())
                        .suggests((context, suggestions) -> suggestOnlinePlayers(suggestions))
                        .executes(this::add)));
        builder.then(friendRemoval("delete"));
        builder.then(friendRemoval("remove"));
        builder.then(LiteralArgumentBuilder.literal("list").executes(context -> list()));
        builder.then(LiteralArgumentBuilder.literal("clear").executes(context -> clear()));
    }

    private int showUsage() {
        ChatUtil.usage("friend add <name>  •  delete <name>  •  list  •  clear");
        return 1;
    }

    private int add(CommandContext<Object> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!FriendManager.isValidName(name)) {
            ChatUtil.error("Invalid player name  •  " + name);
            return 0;
        }
        if (!FriendManager.INSTANCE.add(name)) {
            ChatUtil.error(name + " is already a friend");
            return 0;
        }
        ChatUtil.success(name + " added to friends");
        return 1;
    }

    private int remove(CommandContext<Object> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!FriendManager.INSTANCE.remove(name)) {
            ChatUtil.error("Friend not found  •  " + name);
            return 0;
        }
        ChatUtil.success(name + " removed from friends");
        return 1;
    }

    private int list() {
        Collection<String> friends = FriendManager.INSTANCE.getFriends();
        if (friends.isEmpty()) {
            ChatUtil.info("Friend list is empty");
            return 1;
        }
        ChatUtil.header("Friends  •  " + friends.size());
        friends.forEach(name -> ChatUtil.entry(":bust_in_silhouette:", name, null));
        return 1;
    }

    private int clear() {
        if (!FriendManager.INSTANCE.clear()) {
            ChatUtil.info("Friend list is already empty");
            return 1;
        }
        ChatUtil.success("Friend list cleared");
        return 1;
    }

    private LiteralArgumentBuilder<Object> friendRemoval(String literal) {
        return LiteralArgumentBuilder.<Object>literal(literal)
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests((context, suggestions) -> suggest(suggestions, FriendManager.INSTANCE.getFriends()))
                        .executes(this::remove));
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return builder.buildFuture();
        }
        return suggest(builder, minecraft.getConnection().getOnlinePlayers().stream()
                .map(info -> info.getProfile().name())
                .filter(name -> !FriendManager.INSTANCE.isFriend(name))
                .toList());
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<String> values) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

}
