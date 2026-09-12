package org.shizavisuals.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;

public abstract class ClientCommand {
    private final String name;
    private final String description;
    private final String emoji;

    protected ClientCommand(String name, String description) {
        this(name, description, ":small_blue_diamond:");
    }

    protected ClientCommand(String name, String description, String emoji) {
        this.name = name;
        this.description = description;
        this.emoji = emoji;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final String emoji() {
        return emoji;
    }

    public List<String> aliases() {
        return List.of();
    }

    public abstract void build(LiteralArgumentBuilder<Object> builder);
}
