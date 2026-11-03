package org.shizavisuals.menu.ps4;

import net.minecraft.resources.Identifier;

/** Textures of the PS4 (deluxe PS4) main menu. */
public final class Ps4Textures {
    private static Identifier icon(String name) {
        return Identifier.parse("shizavisuals:textures/ps4/icons/" + name + ".png");
    }

    public static final Identifier ICON_ACCOUNTS = icon("accounts");
    public static final Identifier ICON_BACK = icon("back");
    public static final Identifier ICON_BELL = icon("bell");
    public static final Identifier ICON_CHEVRON = icon("chevron");
    public static final Identifier ICON_COS_MENU = icon("cos_menu");
    public static final Identifier ICON_DELETE = icon("delete");
    public static final Identifier ICON_DELUXE = icon("deluxe");
    public static final Identifier ICON_DICE = icon("dice");
    public static final Identifier ICON_EXIT = icon("exit");
    public static final Identifier ICON_GRASS = icon("grass");
    public static final Identifier ICON_LOGO = icon("logo");
    public static final Identifier ICON_MUSIC_OFF = icon("music_off");
    public static final Identifier ICON_MUSIC_ON = icon("music_on");
    public static final Identifier ICON_PERSON = icon("person");
    public static final Identifier ICON_PLUS = icon("plus");
    public static final Identifier ICON_RENAME = icon("rename");
    public static final Identifier ICON_SEARCH = icon("search");
    public static final Identifier ICON_SETTINGS = icon("settings");
    public static final Identifier ICON_SINGLEPLAYER = icon("singleplayer");
    public static final Identifier ICON_USER = icon("user");

    private Ps4Textures() {
    }
}
