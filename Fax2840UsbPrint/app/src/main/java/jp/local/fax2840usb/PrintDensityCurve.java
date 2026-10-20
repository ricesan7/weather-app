package jp.local.fax2840usb;

final class PrintDensityCurve {
    private static final int MIN_DENSITY = 1;
    private static final int MAX_DENSITY = 15;
    private static final int DEFAULT_DENSITY = 5;
    private static final double MIDTONE_GAMMA = 0.78;

    private PrintDensityCurve() {}

    static int adjustedLuminance(int luminance, int density) {
        int lum = Math.max(0, Math.min(255, luminance));
        int d = Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, density));

        // Preserve hard black and pure/near-white so text stays crisp and
        // blank paper does not become gray at high density.
        if (lum <= 32 || lum >= 252) return lum;

        double normalized = lum / 255.0;
        int adjusted = (int)Math.round(Math.pow(normalized, MIDTONE_GAMMA) * 255.0);

        // Levels 1..10 retain the existing response. Levels 11..15 add a
        // stronger nonlinear midtone boost so pale spreadsheet cell fills
        // survive monochrome dithering.
        int densityOffset = (DEFAULT_DENSITY - d) * 10;
        if (d > 10) {
            densityOffset -= (d - 10) * 12;
        }

        adjusted += densityOffset;
        return Math.max(0, Math.min(255, adjusted));
    }
}
