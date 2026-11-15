package org.shizavisuals.mixin.accessor;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("mouseGrabbed")
    boolean getMouseGrabbed();

    @Accessor("xpos")
    double getRawXpos();

    @Accessor("ypos")
    double getRawYpos();

    @Accessor("xpos")
    void setRawXpos(double value);

    @Accessor("ypos")
    void setRawYpos(double value);
}
