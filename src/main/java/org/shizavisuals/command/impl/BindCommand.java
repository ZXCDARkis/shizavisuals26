package org.shizavisuals.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.command.KeyNames;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.utils.text.ChatUtil;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class BindCommand extends ClientCommand {
    public BindCommand() {
        super("bind", "Binds a key or mouse button to a feature", ":keyboard:");
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.usage("bind set <feature> <key>  •  remove <feature>  •  list");
            return 1;
        });
        builder.then(LiteralArgumentBuilder.literal("set")
                .then(RequiredArgumentBuilder.argument("feature", StringArgumentType.word())
                        .suggests((context, suggestions) -> suggestFeatures(suggestions))
                        .then(RequiredArgumentBuilder.argument("key", StringArgumentType.word())
                                .suggests((context, suggestions) -> suggest(suggestions, KeyNames.suggestions()))
                                .executes(this::executeSet))));
        builder.then(LiteralArgumentBuilder.literal("remove")
                .then(RequiredArgumentBuilder.argument("feature", StringArgumentType.word())
                        .suggests((context, suggestions) -> suggestFeatures(suggestions))
                        .executes(this::executeRemove)));
        builder.then(LiteralArgumentBuilder.literal("list").executes(this::executeList));
    }

    private int executeSet(CommandContext<Object> context) {
        Feature feature = findFeature(context);
        if (feature == null) {
            return 0;
        }

        String keyName = StringArgumentType.getString(context, "key");
        int bindCode = KeyNames.parse(keyName);
        if (bindCode == BindSetting.UNBOUND) {
            ChatUtil.error("Unknown key  •  " + keyName);
            return 0;
        }

        feature.setBind(bindCode);
        FeatureManager.INSTANCE.save();
        ChatUtil.success("Bound " + feature.getName() + " to " + BindSetting.describe(bindCode));
        return 1;
    }

    private int executeRemove(CommandContext<Object> context) {
        Feature feature = findFeature(context);
        if (feature == null) {
            return 0;
        }
        feature.clearBind();
        FeatureManager.INSTANCE.save();
        ChatUtil.success("Unbound " + feature.getName());
        return 1;
    }

    private int executeList(CommandContext<Object> context) {
        boolean any = false;
        for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
            if (!feature.supportsBinds()) {
                continue;
            }
            BindSetting bind = feature.getBind();
            if (!bind.isBound()) {
                continue;
            }
            any = true;
            StringBuilder keys = new StringBuilder();
            for (int index = 0; index < bind.size(); index++) {
                if (index > 0) {
                    keys.append(", ");
                }
                keys.append(BindSetting.describe(bind.get(index)));
            }
            ChatUtil.entry(":link:", feature.getName(), keys.toString());
        }
        if (!any) {
            ChatUtil.info("No features are currently bound");
        }
        return 1;
    }

    private Feature findFeature(CommandContext<Object> context) {
        String name = StringArgumentType.getString(context, "feature");
        Feature feature = FeatureManager.INSTANCE.getFeature(name);
        if (feature == null) {
            ChatUtil.error("Feature not found  •  " + name);
        } else if (!feature.supportsBinds()) {
            ChatUtil.error("Feature does not support binds  •  " + name);
            return null;
        }
        return feature;
    }

    private CompletableFuture<Suggestions> suggestFeatures(SuggestionsBuilder builder) {
        return suggest(
                builder,
                FeatureManager.INSTANCE.getFeatures().stream()
                        .filter(Feature::supportsBinds)
                        .map(Feature::getName)
                        .toList()
        );
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
