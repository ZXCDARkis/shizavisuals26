package org.shizavisuals.menu.ps5;

import net.minecraft.resources.Identifier;

/** PS5 main menu textures, 1:1 with the deluxe asset layout. */
public final class Ps5Textures {
    public static final Identifier BG_MENU = background("menu");
    public static final Identifier BG_MENU_BLUR = background("menu_blur");
    public static final Identifier BG_LOADING = background("loading");
    public static final Identifier BG_WELCOME = background("welcome");
    public static final Identifier BG_NOISE = background("noise_original");
    public static final Identifier BG_SHADOW = background("shadow");

    public static Identifier icon(String name) {
        return Identifier.parse("shizavisuals:textures/ps5/icons/" + name + ".png");
    }

    public static final Identifier ICON_LOGO = icon("logo");
    public static final Identifier ICON_ARROW = icon("arrow");
    public static final Identifier ICON_PERSON = icon("person");
    public static final Identifier ICON_TELEGRAM = icon("telegram");
    public static final Identifier ICON_DISCORD = icon("discord");
    public static final Identifier ICON_CHEVRON = icon("chevron");
    public static final Identifier ICON_EXIT = icon("exit");
    public static final Identifier ICON_SETTINGS = icon("settings");
    public static final Identifier ICON_ACCOUNTS = icon("accounts");
    public static final Identifier ICON_SINGLEPLAYER = icon("singleplayer");
    public static final Identifier ICON_BELL = icon("bell");
    public static final Identifier ICON_PATTERN = icon("pattern");
    public static final Identifier ICON_SEARCH = icon("search");
    public static final Identifier ICON_BACK = icon("back");
    public static final Identifier ICON_DICE = icon("dice");
    public static final Identifier ICON_PLUS = icon("plus");
    public static final Identifier ICON_USER = icon("user");
    public static final Identifier ICON_DELETE = icon("delete");
    public static final Identifier ICON_RENAME = icon("rename");
    public static final Identifier ICON_GRASS = icon("grass");
    public static final Identifier ICON_SERIF = icon("deluxe_serif");
    public static final Identifier ICON_COSMENU = icon("cos_menu");

    private Ps5Textures() {
    }

    private static Identifier background(String name) {
        return Identifier.parse("shizavisuals:textures/ps5/background/" + name + ".png");
    }
}
