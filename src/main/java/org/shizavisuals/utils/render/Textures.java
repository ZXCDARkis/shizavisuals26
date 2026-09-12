package org.shizavisuals.utils.render;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Textures {

    private static final List<Identifier> ALL = new ArrayList<>();

    public static final Identifier TARGET = texture("target.png");

    public static final class Esp {
        public static final Identifier GLOW = texture("esp/glow.png");
        public static final Identifier VELVET = texture("esp/velvet_petals.png");

        private Esp() {
        }
    }

    private Textures() {
    }

    public static List<Identifier> all() {

        touch(Logos.BOOT, Hud.ARROW_OUTLINE, Shader.BLOOM, Header.SEARCH, Icons.BOXES, Esp.GLOW, Esp.VELVET);
        synchronized (ALL) {
            return List.copyOf(ALL);
        }
    }

    private static void touch(Identifier... constants) {
    }

    public static final class Logos {
        public static final Identifier BOOT = texture("menu/logo_boot.svg");
        public static final Identifier BOLT = texture("hud/logo_bolt.svg");

        private Logos() {
        }
    }

    public static final class Hud {
        public static final Identifier ARROW_OUTLINE = texture("hud/arrow.png");
        public static final Identifier ARROW_FILLED = texture("hud/filled_arrow.png");

        private Hud() {
        }
    }

    public static final class Shader {
        public static final Identifier BLOOM = texture("shader/bloom.png");
        public static final Identifier JUMP_FREQUENCY = texture("shader/jump_frequency.png");

        private Shader() {
        }
    }

    public static final class Header {
        public static final Identifier SEARCH = svg("header/search");
        public static final Identifier CHEVRON_LEFT = svg("header/chevron_left");
        public static final Identifier CHEVRON_RIGHT = svg("header/chevron_right");
        public static final Identifier SETTINGS = svg("header/settings");
        public static final Identifier FRIENDS = svg("header/friends");
        public static final Identifier PROFILE_ADD = svg("header/profile_add");
        public static final Identifier DOCUMENT = svg("header/document");
        public static final Identifier DEV_AVATAR = texture("menu/header/dev.png");

        private Header() {
        }
    }

    public static final class Icons {
        public static final Identifier BOXES = svg("icons/boxes");
        public static final Identifier BRAIN = svg("icons/brain");
        public static final Identifier CHEVRON_DOWN = svg("icons/chevron_down");
        public static final Identifier CHEVRONS_LEFT_RIGHT = svg("icons/chevrons_left_right");
        public static final Identifier CHEVRONS_LEFT_RIGHT_ELLIPSIS = svg("icons/chevrons_left_right_ellipsis");
        public static final Identifier CIRCLE_PLUS = svg("icons/circle_plus");
        public static final Identifier COMMAND = svg("icons/command");
        public static final Identifier DELETE = svg("icons/delete");
        public static final Identifier DELETE_LEFT = svg("icons/delete_left");
        public static final Identifier DICES = svg("icons/dices");
        public static final Identifier EYE = svg("icons/eye");
        public static final Identifier GAMEPAD = svg("icons/gamepad_2");
        public static final Identifier GIFT = svg("icons/gift");
        public static final Identifier HARD_DRIVE = svg("icons/hard_drive");
        public static final Identifier KEYBOARD = svg("icons/keyboard");
        public static final Identifier MOVE_3D = svg("icons/move_3d");
        public static final Identifier OPTION = svg("icons/option");
        public static final Identifier PERSON_STANDING = svg("icons/person_standing");
        public static final Identifier PIN = svg("icons/pin");
        public static final Identifier PLUS = svg("icons/plus");
        public static final Identifier REFRESH_CCW = svg("icons/refresh_ccw");
        public static final Identifier SCAN_HEART = svg("icons/scan_heart");
        public static final Identifier SPARKLES = svg("icons/sparkles");
        public static final Identifier SWORDS = svg("icons/swords");
        public static final Identifier TRIANGLE_ALERT = svg("icons/triangle_alert");
        public static final Identifier USER_ROUND = svg("icons/user_round");
        public static final Identifier USER_ROUND_PLUS = svg("icons/user_round_plus");

        private Icons() {
        }
    }

    private static Identifier svg(String menuPath) {

        return texture("menu/" + menuPath + ".svg");
    }

    private static Identifier texture(String path) {
        return register(Identifier.parse("shizavisuals:textures/" + path));
    }

    private static Identifier register(Identifier id) {

        synchronized (ALL) {
            ALL.add(id);
        }
        return id;
    }
}
