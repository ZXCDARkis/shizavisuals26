package org.shizavisuals.utils.render.world;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface WorldEffect {

    boolean active();

    void render(WorldEffectContext context);

    default void release() {
    }

    static <F> WorldEffect direct(Supplier<F> gate, BiConsumer<F, WorldEffectContext> render) {
        return new WorldEffect() {
            private F feature;

            @Override
            public boolean active() {
                this.feature = gate.get();
                return this.feature != null;
            }

            @Override
            public void render(WorldEffectContext context) {
                render.accept(this.feature, context);
            }
        };
    }

    static <F, R> WorldEffect lazy(Supplier<F> gate,
                                   Supplier<R> factory,
                                   RenderCall<F, R> render,
                                   Consumer<R> release) {
        return new WorldEffect() {
            private F feature;
            private R renderer;

            @Override
            public boolean active() {
                this.feature = gate.get();
                return this.feature != null;
            }

            @Override
            public void render(WorldEffectContext context) {
                if (this.renderer == null) {
                    this.renderer = factory.get();
                }
                render.render(this.feature, this.renderer, context);
            }

            @Override
            public void release() {
                if (this.renderer != null) {
                    release.accept(this.renderer);
                    this.renderer = null;
                }
            }
        };
    }

    @FunctionalInterface
    interface RenderCall<F, R> {
        void render(F feature, R renderer, WorldEffectContext context);
    }
}
