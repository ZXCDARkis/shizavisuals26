package org.shizavisuals.utils.render.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class PulseCosmeticModel extends Model<AvatarRenderState> {

    public static final String NAMESPACE = "shizavisuals";
    public static final String RESOURCE_ROOT = "/assets/" + NAMESPACE + "/cosmetics/pulse/";

    public static Identifier texture(String category, String id) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, "cosmetics/pulse/" + category + "/" + id + "/texture.png");
    }

    private static final float[] LOCAL_ORIGIN = {0.0F, 0.0F, 0.0F};
    private static final float[] PLAYER_ORIGIN = {0.0F, 24.0F, 0.0F};
    private static final float[] ZERO_ROTATION = {0.0F, 0.0F, 0.0F};

    private static final float HEAD_TOP_Y = -0.5F;
    private static final float HAT_SINK_PIXELS = 2.0F;
    private static final float PET_CLEARANCE_X = 0.375F;
    private static final float PET_SINK_PIXELS = 1.5F;
    private static final float BODY_CENTER_Y = 0.375F;
    private static final float BODY_FRONT_Z = -0.125F;
    private static final int ANIMATION_SAMPLES = 12;

    private static final List<FaceDirection> FACE_DIRECTIONS = List.of(
            new FaceDirection("down", Direction.DOWN),
            new FaceDirection("up", Direction.UP),
            new FaceDirection("west", Direction.WEST),
            new FaceDirection("north", Direction.NORTH),
            new FaceDirection("east", Direction.EAST),
            new FaceDirection("south", Direction.SOUTH)
    );

    private static final List<String> RESTING_TOKENS = List.of("idle", "main", "anim");
    private static final List<String> ELYTRA_TOKENS = List.of("elytra", "flying");
    private static final List<String> SWIM_TOKENS = List.of("swimming", "swimmings", "water_deep", "water");
    private static final List<String> MOVE_TOKENS = List.of("moving", "walking", "walk", "run");
    private static final List<String> IDLE_TOKENS = List.of("idle", "main", "anim", "animation");

    private final Transform transform;
    private final Attachment attachment;
    private final Map<String, ModelPart> bones;
    private final List<Animation> animations;

    private PulseCosmeticModel(ModelPart root, Transform transform, Attachment attachment,
                               Map<String, ModelPart> bones, List<Animation> animations) {
        super(root, RenderTypes::entityTranslucent);
        this.transform = transform;
        this.attachment = attachment;
        this.bones = bones;
        this.animations = animations;
    }

    public Transform getTransform() {
        return this.transform;
    }

    public Attachment getAttachment() {
        return this.attachment;
    }

    public Map<String, ModelPart> getBones() {
        return this.bones;
    }

    public List<Animation> getAnimations() {
        return this.animations;
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        resetPose();
        Animation animation = chooseAnimation(state);
        if (animation != null) {
            float time = animation.length() > 0.0F ? state.ageInTicks / 20.0F % animation.length() : 0.0F;
            applyAnimation(this.bones, animation, time);
        }
    }

    private Animation chooseAnimation(AvatarRenderState state) {
        if (this.animations.isEmpty()) {
            return null;
        }

        List<Animation> usable = new ArrayList<>(this.animations.size());
        for (Animation animation : this.animations) {
            if (!animation.name().toLowerCase(Locale.ROOT).contains("gui")) {
                usable.add(animation);
            }
        }
        if (usable.isEmpty()) {
            return this.animations.get(0);
        }

        List<String> movementTokens = movementTokens(state);
        List<Animation> candidates = new ArrayList<>(usable.size());
        for (Animation animation : usable) {
            if (containsAnyToken(animation.name().toLowerCase(Locale.ROOT), movementTokens)) {
                candidates.add(animation);
            }
        }
        if (candidates.isEmpty()) {
            candidates = usable;
        }

        Animation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Animation animation : candidates) {
            int score = score(animation.name().toLowerCase(Locale.ROOT), state, movementTokens);
            if (score > bestScore) {
                bestScore = score;
                best = animation;
            }
        }
        return best;
    }

    private static List<String> movementTokens(AvatarRenderState state) {
        if (state.isFallFlying) {
            return ELYTRA_TOKENS;
        }
        if (state.isVisuallySwimming || state.swimAmount > 0.15F) {
            return SWIM_TOKENS;
        }
        if (state.walkAnimationSpeed > 0.04F) {
            return MOVE_TOKENS;
        }
        return IDLE_TOKENS;
    }

    private static int score(String name, AvatarRenderState state, List<String> movementTokens) {
        int score = 0;
        if (state.isCrouching && name.contains("sneak")) {
            score += 4;
        }
        if (!state.isCrouching && !name.contains("sneak")) {
            score += 2;
        }
        if (state.isInWater && name.contains("water")) {
            score += 3;
        }
        if (!state.isInWater && !name.contains("water")) {
            score += 1;
        }
        if (containsAnyToken(name, movementTokens)) {
            score += 8;
        }
        return score;
    }

    private static boolean containsAnyToken(String name, List<String> tokens) {
        for (String token : tokens) {
            if (name.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static PulseCosmeticModel load(String category, String id) {
        String resourceRoot = RESOURCE_ROOT + category + "/" + id;
        JsonObject geometryDocument = readJson(resourceRoot + "/geometry.json");
        JsonObject config = readJson(resourceRoot + "/config.json");
        JsonObject animationDocument = readOptionalJson(resourceRoot + "/animation.json");

        JsonObject geometry = geometryDocument.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float declaredWidth = description.get("texture_width").getAsFloat();
        float declaredHeight = description.get("texture_height").getAsFloat();
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones == null) {
            throw new IllegalStateException("Pulse cosmetic geometry has no bones: " + resourceRoot);
        }

        float[] imageSize = textureImageSize(category, id, declaredWidth, declaredHeight);
        float textureWidth = imageSize[0];
        float textureHeight = imageSize[1];

        JsonElement playerOrigin = config.get("player_origin");
        float[] referencePivot = (playerOrigin != null && playerOrigin.getAsBoolean())
                ? PLAYER_ORIGIN
                : referenceOriginFor(category);

        Geometry baked = bakeGeometry(bones, textureWidth, textureHeight, referencePivot);
        float configuredScale = floatOr(config, "scale", 1.0F);
        Attachment attachment = attachmentFor(category, config);
        List<Animation> animations = parseAnimations(animationDocument);
        float[] placement = placementFor(category, baked, animations, configuredScale, config);

        return new PulseCosmeticModel(
                baked.root(),
                new Transform(
                        placement[0],
                        placement[1],
                        placement[2],
                        configuredScale,
                        floatOr(config, "yaw", 0.0F),
                        floatOr(config, "pitch", 0.0F),
                        floatOr(config, "roll", 0.0F)
                ),
                attachment,
                baked.bones(),
                animations
        );
    }

    private static float[] referenceOriginFor(String category) {
        return "pet".equals(category) ? PLAYER_ORIGIN : LOCAL_ORIGIN;
    }

    /**
     * Resolves the real pixel size of the shipped texture. Geometry files declare
     * the texture space they were authored in, which is often smaller than the
     * PNG itself (for example wings 61 declares 258x387 while the image is
     * 516x774). UVs must be normalized against the actual image, otherwise the
     * model samples the wrong region and the cosmetic looks scrambled.
     */
    static float[] textureImageSize(String category, String id, float declaredWidth, float declaredHeight) {
        float[] size = readPngSize(RESOURCE_ROOT + category + "/" + id + "/texture.png");
        if (size == null) {
            return new float[]{declaredWidth, declaredHeight};
        }
        return size;
    }

    private static float[] readPngSize(String resourcePath) {
        try (java.io.InputStream inputStream = PulseCosmeticModel.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            byte[] header = inputStream.readNBytes(24);
            if (header.length < 24
                    || (header[0] & 0xFF) != 0x89
                    || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                return null;
            }
            long width = ((long) (header[16] & 0xFF) << 24)
                    | ((long) (header[17] & 0xFF) << 16)
                    | ((long) (header[18] & 0xFF) << 8)
                    | (header[19] & 0xFF);
            long height = ((long) (header[20] & 0xFF) << 24)
                    | ((long) (header[21] & 0xFF) << 16)
                    | ((long) (header[22] & 0xFF) << 8)
                    | (header[23] & 0xFF);
            if (width <= 0L || height <= 0L) {
                return null;
            }
            return new float[]{(float) width, (float) height};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static float[] placementFor(String category, Geometry baked, List<Animation> animations,
                                        float scale, JsonObject config) {
        float[] resting = {
                floatOr(config, "x", 0.0F),
                -floatOr(config, "y", 0.0F),
                floatOr(config, "z", 0.0F)
        };
        if ("wings".equals(category)) {
            return new float[]{resting[0], resting[1] + 0.25F, resting[2]};
        }

        Bounds restingBounds = extentsOf(baked.root(), scale);
        if (isAuthoredOnPlayer(config)) {
            return dropOntoHead(category, resting, restingBounds, config);
        }

        Bounds bounds = boundsOf(baked, animations, scale);
        if (bounds == null) {
            return resting;
        }

        Bounds reference = restingBounds != null ? restingBounds : bounds;
        float bottom = reference.maxY();
        return switch (category) {
            case "hat" -> new float[]{
                    -bounds.centerX(),
                    HEAD_TOP_Y + floatOr(config, "sink", HAT_SINK_PIXELS) / 16.0F - bottom,
                    -bounds.centerZ()
            };
            case "pet" -> new float[]{
                    bounds.centerX() < 0.0F
                            ? -PET_CLEARANCE_X - bounds.maxX()
                            : PET_CLEARANCE_X - bounds.minX(),
                    PET_SINK_PIXELS / 16.0F - bottom,
                    -bounds.centerZ()
            };
            case "bodywear" -> new float[]{
                    -bounds.centerX(),
                    BODY_CENTER_Y - bounds.centerY(),
                    BODY_FRONT_Z - bounds.minZ()
            };
            default -> resting;
        };
    }

    private static float[] dropOntoHead(String category, float[] configured, Bounds resting, JsonObject config) {
        if (!"hat".equals(category) || resting == null) {
            return configured;
        }
        float rest = HEAD_TOP_Y + floatOr(config, "sink", HAT_SINK_PIXELS) / 16.0F;
        return resting.maxY() + configured[1] >= rest
                ? configured
                : new float[]{configured[0], rest - resting.maxY(), configured[2]};
    }

    private static boolean isAuthoredOnPlayer(JsonObject config) {
        if (!config.has("attachment")) {
            return false;
        }
        JsonElement playerOrigin = config.get("player_origin");
        return playerOrigin == null || !playerOrigin.getAsBoolean();
    }

    private static Bounds boundsOf(Geometry baked, List<Animation> animations, float scale) {
        Bounds bounds = extentsOf(baked.root(), scale);
        Animation resting = restingAnimation(animations);
        if (resting == null) {
            return bounds;
        }

        for (int step = 0; step <= ANIMATION_SAMPLES; step++) {
            baked.root().resetPose();
            applyAnimation(baked.bones(), resting, resting.length() * step / ANIMATION_SAMPLES);
            bounds = union(bounds, extentsOf(baked.root(), scale));
        }
        baked.root().resetPose();
        return bounds;
    }

    private static Animation restingAnimation(List<Animation> animations) {
        List<Animation> usable = new ArrayList<>(animations.size());
        for (Animation animation : animations) {
            if (!animation.name().toLowerCase(Locale.ROOT).contains("gui")) {
                usable.add(animation);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        for (Animation animation : usable) {
            String name = animation.name().toLowerCase(Locale.ROOT);
            if (containsAnyToken(name, RESTING_TOKENS) && !name.contains("sneak") && !name.contains("water")) {
                return animation;
            }
        }
        return usable.get(0);
    }

    private static Bounds extentsOf(ModelPart root, float scale) {
        BoundsAccumulator accumulator = new BoundsAccumulator();
        root.getExtentsForGui(new PoseStack(), accumulator);
        return accumulator.toBounds(scale);
    }

    private static Bounds union(Bounds first, Bounds other) {
        if (first == null) {
            return other;
        }
        if (other == null) {
            return first;
        }
        return new Bounds(
                Math.min(first.minX(), other.minX()),
                Math.min(first.minY(), other.minY()),
                Math.min(first.minZ(), other.minZ()),
                Math.max(first.maxX(), other.maxX()),
                Math.max(first.maxY(), other.maxY()),
                Math.max(first.maxZ(), other.maxZ())
        );
    }

    private static void applyAnimation(Map<String, ModelPart> bones, Animation animation, float time) {
        for (Map.Entry<String, BoneTrack> entry : animation.tracks().entrySet()) {
            String boneName = entry.getKey();
            BoneTrack track = entry.getValue();
            ModelPart part = bones.get(boneName);
            if (part == null) {
                part = findIgnoreCase(bones, boneName);
            }
            if (part == null) {
                continue;
            }

            float[] rotation = sample(track.rotation(), time);
            if (rotation != null) {
                part.xRot += degrees(rotation[0]);
                part.yRot += degrees(rotation[1]);
                part.zRot += degrees(rotation[2]);
            }

            float[] position = sample(track.position(), time);
            if (position != null) {
                part.x += position[0];
                part.y -= position[1];
                part.z += position[2];
            }

            float[] scale = sample(track.scale(), time);
            if (scale != null) {
                part.xScale *= scale[0];
                part.yScale *= scale[1];
                part.zScale *= scale[2];
            }
        }
    }

    private static float[] sample(Channel channel, float time) {
        return channel == null ? null : channel.sample(time);
    }

    private static ModelPart findIgnoreCase(Map<String, ModelPart> bones, String boneName) {
        for (Map.Entry<String, ModelPart> entry : bones.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(boneName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Geometry bakeGeometry(JsonArray bones, float textureWidth, float textureHeight, float[] referencePivot) {
        List<JsonObject> boneObjects = new ArrayList<>(bones.size());
        for (JsonElement bone : bones) {
            boneObjects.add(bone.getAsJsonObject());
        }

        Map<String, List<JsonObject>> childrenByParent = new LinkedHashMap<>();
        for (JsonObject bone : boneObjects) {
            JsonElement parent = bone.get("parent");
            childrenByParent
                    .computeIfAbsent(parent != null ? parent.getAsString() : null, ignored -> new ArrayList<>())
                    .add(bone);
        }

        Map<String, ModelPart> bakedBones = new LinkedHashMap<>();
        Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        for (JsonObject bone : childrenByParent.getOrDefault(null, List.of())) {
            rootChildren.put(
                    bone.get("name").getAsString(),
                    buildBone(childrenByParent, textureWidth, textureHeight, bakedBones, bone, referencePivot)
            );
        }

        ModelPart root = withPose(new ModelPart(List.of(), rootChildren), PartPose.ZERO);
        return new Geometry(root, bakedBones);
    }

    private static ModelPart buildBone(Map<String, List<JsonObject>> childrenByParent, float textureWidth, float textureHeight,
                                       Map<String, ModelPart> bakedBones, JsonObject bone, float[] parentPivot) {
        String name = bone.get("name").getAsString();
        float[] pivot = floats(bone.getAsJsonArray("pivot"), 3);

        Map<String, ModelPart> children = new LinkedHashMap<>();
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null) {
            for (int index = 0; index < cubes.size(); index++) {
                children.put("cube_" + index,
                        buildCubePart(cubes.get(index).getAsJsonObject(), pivot, textureWidth, textureHeight));
            }
        }

        JsonArray meshes = bone.getAsJsonArray("meshes");
        if (meshes != null) {
            for (int index = 0; index < meshes.size(); index++) {
                children.put("mesh_" + index,
                        buildMeshPart(meshes.get(index).getAsJsonObject(), pivot, textureWidth, textureHeight));
            }
        }

        for (JsonObject child : childrenByParent.getOrDefault(name, List.of())) {
            children.put(
                    child.get("name").getAsString(),
                    buildBone(childrenByParent, textureWidth, textureHeight, bakedBones, child, pivot)
            );
        }

        float[] rotation = rotationOrZero(bone.getAsJsonArray("rotation"));
        ModelPart part = new ModelPart(List.of(), children);
        ModelPart posed = withPose(part, PartPose.offsetAndRotation(
                pivot[0] - parentPivot[0],
                parentPivot[1] - pivot[1],
                pivot[2] - parentPivot[2],
                degrees(rotation[0]),
                degrees(rotation[1]),
                degrees(rotation[2])
        ));
        bakedBones.put(name, posed);
        return posed;
    }

    private static ModelPart buildCubePart(JsonObject cubeJson, float[] bonePivot, float textureWidth, float textureHeight) {
        float[] origin = floats(cubeJson.getAsJsonArray("origin"), 3);
        float[] size = floats(cubeJson.getAsJsonArray("size"), 3);
        float[] pivot = floatsOrNull(cubeJson.getAsJsonArray("pivot"), 3);
        if (pivot == null) {
            pivot = bonePivot;
        }
        float[] rotation = rotationOrZero(cubeJson.getAsJsonArray("rotation"));
        float inflate = floatOr(cubeJson, "inflate", 0.0F);
        JsonElement mirrorElement = cubeJson.get("mirror");
        boolean mirror = mirrorElement != null && mirrorElement.getAsBoolean();

        float localX = origin[0] - pivot[0];
        float localY = pivot[1] - origin[1] - size[1];
        float localZ = origin[2] - pivot[2];

        JsonElement uv = cubeJson.get("uv");
        Cube cube;
        if (uv != null && uv.isJsonObject()) {
            cube = buildPerFaceCube(uv.getAsJsonObject(), localX, localY, localZ, size, inflate, mirror,
                    textureWidth, textureHeight);
        } else {
            float[] offset = floatsOrNull(uv != null ? uv.getAsJsonArray() : null, 2);
            if (offset == null) {
                offset = new float[]{0.0F, 0.0F};
            }
            cube = new Cube(
                    (int) offset[0],
                    (int) offset[1],
                    localX,
                    localY,
                    localZ,
                    size[0],
                    size[1],
                    size[2],
                    inflate,
                    inflate,
                    inflate,
                    mirror,
                    textureWidth,
                    textureHeight,
                    EnumSet.allOf(Direction.class)
            );
        }

        ModelPart part = new ModelPart(List.of(cube), Map.of());
        return withPose(part, PartPose.offsetAndRotation(
                pivot[0] - bonePivot[0],
                bonePivot[1] - pivot[1],
                pivot[2] - bonePivot[2],
                degrees(rotation[0]),
                degrees(rotation[1]),
                degrees(rotation[2])
        ));
    }

    private static ModelPart buildMeshPart(JsonObject meshJson, float[] bonePivot, float textureWidth, float textureHeight) {
        float[] pivot = floats(meshJson.getAsJsonArray("pivot"), 3);
        float[] rotation = rotationOrZero(meshJson.getAsJsonArray("rotation"));

        List<float[]> sourceVertices = new ArrayList<>();
        for (JsonElement vertex : meshJson.getAsJsonArray("vertices")) {
            sourceVertices.add(floats(vertex.getAsJsonArray(), 3));
        }

        List<Cube> cubes = new ArrayList<>();
        for (JsonElement faceElement : meshJson.getAsJsonArray("faces")) {
            JsonObject face = faceElement.getAsJsonObject();
            List<Integer> indices = new ArrayList<>();
            for (JsonElement index : face.getAsJsonArray("vertices")) {
                indices.add(index.getAsInt());
            }
            JsonArray uv = face.getAsJsonArray("uv");
            if (indices.size() < 3 || indices.size() > 4 || uv.size() != indices.size()) {
                continue;
            }

            ModelPart.Vertex[] vertices = new ModelPart.Vertex[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                float[] source = sourceVertices.get(indices.get(i));
                JsonArray texture = uv.get(i).getAsJsonArray();
                vertices[i] = new ModelPart.Vertex(
                        source[0] - pivot[0],
                        pivot[1] - source[1],
                        source[2] - pivot[2],
                        texture.get(0).getAsFloat() / textureWidth,
                        texture.get(1).getAsFloat() / textureHeight
                );
            }

            Vector3f first = new Vector3f(
                    vertices[1].x() - vertices[0].x(),
                    vertices[1].y() - vertices[0].y(),
                    vertices[1].z() - vertices[0].z()
            );
            Vector3f second = new Vector3f(
                    vertices[2].x() - vertices[0].x(),
                    vertices[2].y() - vertices[0].y(),
                    vertices[2].z() - vertices[0].z()
            );
            Vector3fc normal = first.cross(second).normalize();

            Cube cube = new Cube(
                    0,
                    0,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    false,
                    textureWidth,
                    textureHeight,
                    EnumSet.of(Direction.NORTH)
            );
            cube.polygons[0] = new ModelPart.Polygon(vertices, normal);
            cubes.add(cube);
        }

        ModelPart part = new ModelPart(cubes, Map.of());
        return withPose(part, PartPose.offsetAndRotation(
                pivot[0] - bonePivot[0],
                bonePivot[1] - pivot[1],
                pivot[2] - bonePivot[2],
                degrees(rotation[0]),
                degrees(rotation[1]),
                degrees(rotation[2])
        ));
    }

    private static Cube buildPerFaceCube(JsonObject uv, float x, float y, float z, float[] size, float inflate,
                                          boolean mirror, float textureWidth, float textureHeight) {
        List<FaceDirection> faces = new ArrayList<>(FACE_DIRECTIONS.size());
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        for (FaceDirection face : FACE_DIRECTIONS) {
            if (uv.has(face.name())) {
                faces.add(face);
                directions.add(face.direction());
            }
        }

        Cube cube = new Cube(
                0,
                0,
                x,
                y,
                z,
                size[0],
                size[1],
                size[2],
                inflate,
                inflate,
                inflate,
                mirror,
                textureWidth,
                textureHeight,
                directions
        );

        for (int index = 0; index < faces.size(); index++) {
            FaceDirection faceDirection = faces.get(index);
            JsonObject face = uv.getAsJsonObject(faceDirection.name());
            float[] start = floats(face.getAsJsonArray("uv"), 2);
            float[] extent = floats(face.getAsJsonArray("uv_size"), 2);
            ModelPart.Polygon polygon = new ModelPart.Polygon(
                    cube.polygons[index].vertices(),
                    start[0],
                    start[1],
                    start[0] + extent[0],
                    start[1] + extent[1],
                    textureWidth,
                    textureHeight,
                    mirror,
                    faceDirection.direction()
            );

            JsonElement uvRotation = face.get("uv_rotation");
            int quarterTurns = Math.floorMod((uvRotation != null ? uvRotation.getAsInt() : 0) / 90, 4);
            if (quarterTurns != 0) {
                ModelPart.Vertex[] source = polygon.vertices();
                ModelPart.Vertex[] rotated = new ModelPart.Vertex[source.length];
                for (int i = 0; i < source.length; i++) {
                    ModelPart.Vertex donor = source[(i + quarterTurns) % source.length];
                    rotated[i] = source[i].remap(donor.u(), donor.v());
                }
                polygon = new ModelPart.Polygon(rotated, polygon.normal());
            }

            cube.polygons[index] = polygon;
        }

        return cube;
    }

    private static Attachment attachmentFor(String category, JsonObject config) {
        return switch (category) {
            case "hat" -> Attachment.HEAD;
            case "pet" -> Attachment.ROOT;
            case "bodywear" -> Attachment.BODY;
            default -> attachmentFromConfig(category, config);
        };
    }

    private static Attachment attachmentFromConfig(String category, JsonObject config) {
        JsonElement attachment = config.get("attachment");
        if (attachment != null) {
            String value = attachment.getAsString().toLowerCase(Locale.ROOT);
            switch (value) {
                case "body" -> {
                    return Attachment.BODY;
                }
                case "head" -> {
                    return Attachment.HEAD;
                }
                case "root" -> {
                    return Attachment.ROOT;
                }
                default -> {
                }
            }
        }
        JsonElement pos = config.get("pos");
        return attachmentForLegacy(category, pos != null ? pos.getAsInt() : 1);
    }

    private static Attachment attachmentForLegacy(String category, int position) {
        if (position == 2 || "hat".equals(category)) {
            return Attachment.HEAD;
        }
        return "wings".equals(category) || "bodywear".equals(category) ? Attachment.BODY : Attachment.ROOT;
    }

    private static List<Animation> parseAnimations(JsonObject root) {
        if (root == null) {
            return List.of();
        }
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null) {
            return List.of();
        }

        List<Animation> parsed = new ArrayList<>(animations.size());
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String name = entry.getKey();
            JsonObject json = entry.getValue().getAsJsonObject();

            Map<String, BoneTrack> tracks = new LinkedHashMap<>();
            JsonObject bones = json.getAsJsonObject("bones");
            if (bones != null) {
                for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                    JsonObject bone = boneEntry.getValue().getAsJsonObject();
                    tracks.put(boneEntry.getKey(), new BoneTrack(
                            parseChannel(bone.get("rotation")),
                            parseChannel(bone.get("position")),
                            parseChannel(bone.get("scale"))
                    ));
                }
            }

            JsonElement explicitLength = json.get("animation_length");
            float declared = explicitLength != null ? explicitLength.getAsFloat() : 0.0F;
            float inferred = 0.0F;
            for (BoneTrack track : tracks.values()) {
                if (track.rotation() != null) {
                    inferred = Math.max(inferred, track.rotation().lastTime());
                }
                if (track.position() != null) {
                    inferred = Math.max(inferred, track.position().lastTime());
                }
                if (track.scale() != null) {
                    inferred = Math.max(inferred, track.scale().lastTime());
                }
            }

            parsed.add(new Animation(name, Math.max(declared, Math.max(inferred, 0.05F)), tracks));
        }
        return parsed;
    }

    private static Channel parseChannel(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (!element.isJsonArray() && !element.isJsonPrimitive()) {
            List<Keyframe> keyframes = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                Float time = toFloatOrNull(entry.getKey());
                if (time == null) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) {
                    float[] vector = vector(value, ZERO_ROTATION);
                    keyframes.add(new Keyframe(time, vector, vector.clone(), "linear", null));
                    continue;
                }

                JsonObject json = value.getAsJsonObject();
                float[] fallback = ZERO_ROTATION;
                JsonElement vectorElement = json.get("vector");
                JsonElement postElement = json.get("post");
                JsonElement preElement = json.get("pre");
                if (vectorElement != null) {
                    fallback = vector(vectorElement, ZERO_ROTATION);
                } else if (postElement != null) {
                    fallback = vector(postElement, ZERO_ROTATION);
                } else if (preElement != null) {
                    fallback = vector(preElement, ZERO_ROTATION);
                }

                float[] pre = preElement != null ? vector(preElement, fallback) : fallback.clone();
                float[] post = postElement != null ? vector(postElement, fallback) : fallback.clone();
                JsonElement lerpMode = json.get("lerp_mode");
                JsonElement easing = json.get("easing");
                keyframes.add(new Keyframe(
                        time,
                        pre,
                        post,
                        lerpMode != null ? lerpMode.getAsString() : "linear",
                        easing != null ? easing.getAsString() : null
                ));
            }

            if (keyframes.isEmpty()) {
                return null;
            }
            keyframes.sort(Comparator.comparingDouble(keyframe -> (double) keyframe.time()));
            return new Channel(keyframes);
        }

        float[] vector = vector(element, new float[]{0.0F, 0.0F, 0.0F});
        return new Channel(List.of(new Keyframe(0.0F, vector, vector.clone(), "linear", null)));
    }

    private static float[] vector(JsonElement element, float[] fallback) {
        if (element.isJsonArray()) {
            JsonArray values = element.getAsJsonArray();
            int size = values.size();
            if (size == 0) {
                return fallback.clone();
            }
            float[] vector = new float[3];
            for (int i = 0; i < 3; i++) {
                vector[i] = values.get(i < size ? i : size - 1).getAsFloat();
            }
            return vector;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            float value = element.getAsFloat();
            return new float[]{value, value, value};
        }
        return fallback.clone();
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (2.0F * p1
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    private static ModelPart withPose(ModelPart part, PartPose pose) {
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }

    private static float[] floats(JsonArray array, int expectedSize) {
        float[] values = new float[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static float[] floatsOrNull(JsonArray array, int expectedSize) {
        return array == null ? null : floats(array, expectedSize);
    }

    private static float[] rotationOrZero(JsonArray array) {
        float[] rotation = floatsOrNull(array, 3);
        return rotation == null ? ZERO_ROTATION : rotation;
    }

    private static float floatOr(JsonObject json, String name, float fallback) {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsFloat();
    }

    private static Float toFloatOrNull(String value) {
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static float degrees(float value) {
        return (float) Math.toRadians(value);
    }

    private static JsonObject readJson(String resource) {
        JsonObject json = readOptionalJson(resource);
        if (json == null) {
            throw new IllegalStateException("Missing Pulse cosmetic resource: " + resource);
        }
        return json;
    }

    private static JsonObject readOptionalJson(String resource) {
        try (InputStream stream = PulseCosmeticModel.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Pulse cosmetic resource: " + resource, exception);
        }
    }

    public enum Attachment {
        HEAD,
        BODY,
        ROOT
    }

    public record Transform(float x, float y, float z, float scale, float yaw, float pitch, float roll) {
    }

    public record Animation(String name, float length, Map<String, BoneTrack> tracks) {
    }

    public record BoneTrack(Channel rotation, Channel position, Channel scale) {
    }

    public record Keyframe(float time, float[] pre, float[] post, String interpolation, String easing) {
    }

    public static final class Channel {
        private final List<Keyframe> keyframes;

        public Channel(List<Keyframe> keyframes) {
            this.keyframes = keyframes;
        }

        public List<Keyframe> getKeyframes() {
            return this.keyframes;
        }

        public float lastTime() {
            Keyframe last = this.keyframes.isEmpty() ? null : this.keyframes.get(this.keyframes.size() - 1);
            return last == null ? 0.0F : last.time();
        }

        public float[] sample(float time) {
            int size = this.keyframes.size();
            if (size == 1 || time <= this.keyframes.get(0).time()) {
                return this.keyframes.get(0).post().clone();
            }

            int nextIndex = -1;
            for (int i = 0; i < size; i++) {
                if (this.keyframes.get(i).time() > time) {
                    nextIndex = i;
                    break;
                }
            }
            if (nextIndex < 0) {
                return this.keyframes.get(size - 1).post().clone();
            }

            int previousIndex = nextIndex - 1;
            Keyframe previous = this.keyframes.get(previousIndex);
            Keyframe next = this.keyframes.get(nextIndex);
            float span = Math.max(next.time() - previous.time(), 1.0E-4F);
            float progress = Math.clamp((time - previous.time()) / span, 0.0F, 1.0F);

            if ("easeInOutQuad".equals(previous.easing())) {
                progress = progress < 0.5F
                        ? 2.0F * progress * progress
                        : 1.0F - 0.5F * (-2.0F * progress + 2.0F) * (-2.0F * progress + 2.0F);
            }

            if ("catmullrom".equalsIgnoreCase(previous.interpolation())) {
                float[] before = previousIndex > 0 ? this.keyframes.get(previousIndex - 1).post() : previous.post();
                float[] after = nextIndex + 1 < size ? this.keyframes.get(nextIndex + 1).pre() : next.pre();
                float[] sampled = new float[3];
                for (int i = 0; i < 3; i++) {
                    sampled[i] = catmullRom(before[i], previous.post()[i], next.pre()[i], after[i], progress);
                }
                return sampled;
            }

            float[] sampled = new float[3];
            for (int i = 0; i < 3; i++) {
                sampled[i] = previous.post()[i] + (next.pre()[i] - previous.post()[i]) * progress;
            }
            return sampled;
        }
    }

    private record FaceDirection(String name, Direction direction) {
    }

    private record Geometry(ModelPart root, Map<String, ModelPart> bones) {
    }

    private record Bounds(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ
    ) {
        float centerX() {
            return (this.minX + this.maxX) * 0.5F;
        }

        float centerY() {
            return (this.minY + this.maxY) * 0.5F;
        }

        float centerZ() {
            return (this.minZ + this.maxZ) * 0.5F;
        }
    }

    private static final class BoundsAccumulator implements Consumer<Vector3fc> {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        @Override
        public void accept(Vector3fc vertex) {
            this.minX = Math.min(this.minX, vertex.x());
            this.minY = Math.min(this.minY, vertex.y());
            this.minZ = Math.min(this.minZ, vertex.z());
            this.maxX = Math.max(this.maxX, vertex.x());
            this.maxY = Math.max(this.maxY, vertex.y());
            this.maxZ = Math.max(this.maxZ, vertex.z());
        }

        Bounds toBounds(float scale) {
            if (!Float.isFinite(this.minX) || !Float.isFinite(this.maxX)) {
                return null;
            }
            return new Bounds(
                    this.minX * scale,
                    this.minY * scale,
                    this.minZ * scale,
                    this.maxX * scale,
                    this.maxY * scale,
                    this.maxZ * scale
            );
        }
    }
}
