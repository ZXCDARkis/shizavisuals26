package org.shizavisuals.utils.render.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PulseCosmeticModelTest {

    static Stream<PulseCosmeticCatalog.PulseCosmetic> cosmetics() {
        return PulseCosmeticCatalog.all().stream();
    }

    @ParameterizedTest
    @MethodSource("cosmetics")
    void textureUvsAreNormalizedAgainstTheRealImage(PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        String category = cosmetic.category().id();
        String id = cosmetic.id();
        float[] imageSize = imageSize(category, id);
        float[] declared = declaredTextureSize(category, id);
        float[] resolved = PulseCosmeticModel.textureImageSize(category, id, declared[0], declared[1]);

        assertEquals(imageSize[0], resolved[0], 0.01F, "uv width for " + cosmetic.mode());
        assertEquals(imageSize[1], resolved[1], 0.01F, "uv height for " + cosmetic.mode());
    }

    private static float[] imageSize(String category, String id) {
        String path = "/assets/shizavisuals/cosmetics/pulse/" + category + "/" + id + "/texture.png";
        try (java.io.InputStream inputStream = PulseCosmeticModelTest.class.getResourceAsStream(path)) {
            assertNotNull(inputStream, "missing texture for " + category + "/" + id);
            byte[] header = inputStream.readNBytes(24);
            assertEquals(24, header.length, "truncated texture header");
            return new float[]{readInt(header, 16), readInt(header, 20)};
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static float[] declaredTextureSize(String category, String id) {
        String path = "/assets/shizavisuals/cosmetics/pulse/" + category + "/" + id + "/geometry.json";
        try (java.io.InputStream inputStream = PulseCosmeticModelTest.class.getResourceAsStream(path)) {
            assertNotNull(inputStream, "missing geometry for " + category + "/" + id);
            com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(
                    new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                    com.google.gson.JsonObject.class);
            com.google.gson.JsonObject description = root.getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonObject("description");
            return new float[]{description.get("texture_width").getAsFloat(), description.get("texture_height").getAsFloat()};
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static float readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    @ParameterizedTest
    @MethodSource("cosmetics")
    void assetBakesWithExpectedAttachment(PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        PulseCosmeticModel model = PulseCosmeticModel.load(cosmetic.category().id(), cosmetic.id());

        assertEquals(expectedAttachment(cosmetic.category()), model.getAttachment(),
                "attachment for " + cosmetic.mode());
        assertFalse(model.getBones().isEmpty(), "no baked bones for " + cosmetic.mode());
        assertTrue(model.root().getAllParts().size() > 1,
                "expected a non-trivial bone tree for " + cosmetic.mode());

        PulseCosmeticModel.Transform transform = model.getTransform();
        assertTrue(Float.isFinite(transform.x()) && Float.isFinite(transform.y()) && Float.isFinite(transform.z()),
                "non-finite placement for " + cosmetic.mode());
        assertTrue(transform.scale() > 0.0F, "non-positive scale for " + cosmetic.mode());
        assertTrue(Float.isFinite(transform.yaw()) && Float.isFinite(transform.pitch()) && Float.isFinite(transform.roll()),
                "non-finite rotation for " + cosmetic.mode());
    }

    @ParameterizedTest
    @MethodSource("cosmetics")
    void assetBonesHaveRenderableGeometry(PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        PulseCosmeticModel model = PulseCosmeticModel.load(cosmetic.category().id(), cosmetic.id());
        int[] polygons = {0};
        model.root().visit(new PoseStack(), (pose, name, index, cube) -> polygons[0] += cube.polygons.length);
        assertTrue(polygons[0] > 0, "no polygons baked for " + cosmetic.mode());
    }

    @ParameterizedTest
    @MethodSource("cosmetics")
    void assetAnimationsCarrySampleableChannels(PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        PulseCosmeticModel model = PulseCosmeticModel.load(cosmetic.category().id(), cosmetic.id());
        for (PulseCosmeticModel.Animation animation : model.getAnimations()) {
            assertNotNull(animation.tracks(), "missing tracks for " + cosmetic.mode());
            assertTrue(animation.length() >= 0.05F, "non-positive length for " + cosmetic.mode());

            boolean animated = false;
            for (PulseCosmeticModel.BoneTrack track : animation.tracks().values()) {
                if (track.rotation() == null && track.position() == null && track.scale() == null) {
                    continue;
                }
                animated = true;
                if (track.rotation() != null) {
                    assertFinite(track.rotation().sample(0.0F));
                }
                if (track.position() != null) {
                    assertFinite(track.position().sample(0.0F));
                }
                if (track.scale() != null) {
                    assertFinite(track.scale().sample(0.0F));
                }
            }
            assertTrue(animated, "no sampleable channels in " + cosmetic.mode() + " / " + animation.name());
        }
    }

    @Test
    void setupAnimKeepsEveryBoneFinite() {
        PulseCosmeticModel model = PulseCosmeticModel.load("wings", "124");
        assertFalse(model.getAnimations().isEmpty(), "wings/124 should ship an animation");

        AvatarRenderState state = new AvatarRenderState();
        state.ageInTicks = 20.0F;
        model.setupAnim(state);

        List<ModelPart> parts = model.root().getAllParts();
        for (ModelPart part : parts) {
            assertTrue(Float.isFinite(part.xRot) && Float.isFinite(part.yRot) && Float.isFinite(part.zRot),
                    "non-finite rotation after setupAnim");
            assertTrue(Float.isFinite(part.x) && Float.isFinite(part.y) && Float.isFinite(part.z),
                    "non-finite position after setupAnim");
            assertTrue(Float.isFinite(part.xScale) && Float.isFinite(part.yScale) && Float.isFinite(part.zScale),
                    "non-finite scale after setupAnim");
        }
    }

    @Test
    void channelSamplingIsClampedAndFinite() {
        PulseCosmeticModel model = PulseCosmeticModel.load("wings", "124");
        PulseCosmeticModel.Animation animation = model.getAnimations().get(0);
        for (PulseCosmeticModel.BoneTrack track : animation.tracks().values()) {
            for (float time : new float[]{-5.0F, 0.0F, animation.length() * 0.5F, animation.length(), 1000.0F}) {
                if (track.rotation() != null) {
                    assertFinite(track.rotation().sample(time));
                }
                if (track.position() != null) {
                    assertFinite(track.position().sample(time));
                }
                if (track.scale() != null) {
                    assertFinite(track.scale().sample(time));
                }
            }
        }
    }

    @Test
    void missingAssetFailsLoudly() {
        try {
            PulseCosmeticModel.load("wings", "does_not_exist");
            fail("expected a failure for a missing asset");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void assertFinite(float[] values) {
        assertNotNull(values);
        for (float value : values) {
            assertTrue(Float.isFinite(value), "sampled a non-finite value: " + value);
        }
    }

    private static PulseCosmeticModel.Attachment expectedAttachment(PulseCosmeticCategory category) {
        return switch (category) {
            case HAT -> PulseCosmeticModel.Attachment.HEAD;
            case BODYWEAR -> PulseCosmeticModel.Attachment.BODY;
            case WINGS -> PulseCosmeticModel.Attachment.BODY;
            case PET -> PulseCosmeticModel.Attachment.ROOT;
        };
    }
}
