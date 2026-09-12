package org.shizavisuals.utils.render.gui;

public final class UiFonts {
    private UiFonts() {
    }

    public static MsdfFontFamily googleSansFlex() {
        return Holder.GOOGLE_SANS_FLEX;
    }

    public static MsdfFont googleSansFlex(int weight) {
        return Holder.GOOGLE_SANS_FLEX.resolve(weight);
    }

    public static MsdfFontFamily googleSans() {
        return Holder.GOOGLE_SANS;
    }

    public static MsdfFont googleSans(int weight) {
        return Holder.GOOGLE_SANS.resolve(weight);
    }

    public static MsdfFontFamily sfPro() {
        return Holder.SF_PRO;
    }

    public static MsdfFont sfPro(int weight) {
        return Holder.SF_PRO.resolve(weight);
    }

    public static MsdfFont sfProDisplay() {
        return Holder.SF_PRO.regular();
    }

    public static MsdfFont getFontForText(String text, int weight) {
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '\u0400' && c <= '\u04FF') {
                    return googleSans(weight);
                }
            }
        }
        return googleSansFlex(weight);
    }

    private static final class Holder {
        private static final MsdfFontFamily GOOGLE_SANS_FLEX = MsdfFontFamily.builder()
                .variant(400, "shizavisuals:fonts/google_sans_flex_regular.json")
                .variant(500, "shizavisuals:fonts/google_sans_flex_medium.json")
                .variant(600, "shizavisuals:fonts/google_sans_flex_semibold.json")
                .variant(700, "shizavisuals:fonts/google_sans_flex_bold.json")
                .build();

        private static final MsdfFontFamily GOOGLE_SANS = MsdfFontFamily.builder()
                .variant(400, "shizavisuals:fonts/google_sans_regular.json")
                .variant(500, "shizavisuals:fonts/google_sans_medium.json")
                .variant(600, "shizavisuals:fonts/google_sans_semibold.json")
                .variant(700, "shizavisuals:fonts/google_sans_bold.json")
                .build();

        private static final MsdfFontFamily SF_PRO = MsdfFontFamily.builder()
                .variant(400, "shizavisuals:fonts/sf_pro_regular.json")
                .variant(500, "shizavisuals:fonts/sf_pro_medium.json")
                .variant(600, "shizavisuals:fonts/sf_pro_semibold.json")
                .variant(700, "shizavisuals:fonts/sf_pro_bold.json")
                .build();
    }
}
