package org.shizavisuals.mixin.accessor;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    void setRightClickDelay(int rightClickDelay);

    @Accessor("user")
    @Mutable
    void setUser(User user);

    @Accessor("profileFuture")
    @Mutable
    void setProfileFuture(CompletableFuture<ProfileResult> profileFuture);
}
