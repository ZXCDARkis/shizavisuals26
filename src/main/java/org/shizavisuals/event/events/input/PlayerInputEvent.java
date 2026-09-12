package org.shizavisuals.event.events.input;

import lombok.Getter;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.shizavisuals.event.Event;

@Getter
public final class PlayerInputEvent extends Event {
    private final KeyboardInput keyboardInput;
    private Vec2 moveVector;
    private Input keyPresses;

    public PlayerInputEvent(KeyboardInput keyboardInput, Input keyPresses, Vec2 moveVector) {
        this.keyboardInput = keyboardInput;
        this.keyPresses = keyPresses;
        this.moveVector = moveVector;
    }

    public void setShift(boolean shift) {
        Input in = keyPresses;
        keyPresses = new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), shift, in.sprint());
    }

    public void setJump(boolean jump) {
        Input in = keyPresses;
        keyPresses = new Input(in.forward(), in.backward(), in.left(), in.right(), jump, in.shift(), in.sprint());
    }

    public void setSprint(boolean sprint) {
        Input in = keyPresses;
        keyPresses = new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.shift(), sprint);
    }

    public void setMoveVector(Vec2 moveVector) {
        this.moveVector = moveVector == null ? Vec2.ZERO : moveVector;
    }

    public void setKeyPresses(Input keyPresses) {
        this.keyPresses = keyPresses == null ? Input.EMPTY : keyPresses;
    }

    public void clearMovement(boolean keepJump, boolean keepShift) {
        Input in = this.keyPresses;
        this.keyPresses = new Input(
                false,
                false,
                false,
                false,
                keepJump && in.jump(),
                keepShift && in.shift(),
                false
        );
        this.moveVector = Vec2.ZERO;
    }
}
