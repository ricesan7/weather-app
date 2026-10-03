package jp.local.fax2840usb;

/**
 * Pure layout policy for automatic table printing.
 *
 * Sparse pages stay as full pages so a few lines are not enlarged to fill A4.
 * Wide/dense tables may still split horizontally so text does not become tiny.
 */
final class AutoPageLayout {
    private static final float MIN_WIDTH_OCCUPANCY = 0.70f;
    private static final float MIN_HEIGHT_OCCUPANCY = 0.12f;
    private static final float MIN_READABLE_WIDTH_SCALE = 0.85f;
    private static final float TARGET_SEGMENT_ASPECT = 3.6f;
    private static final float LANDSCAPE_SEGMENT_ASPECT = 1.12f;
    private static final int MAX_COLUMNS = 4;

    private AutoPageLayout() {}

    static int columnsForAuto(float pageWidth, float pageHeight,
                              float contentWidth, float contentHeight,
                              float targetWidth) {
        float safePageWidth = Math.max(1f, pageWidth);
        float safePageHeight = Math.max(1f, pageHeight);
        float safeContentWidth = Math.max(1f, Math.min(contentWidth, safePageWidth));
        float safeContentHeight = Math.max(1f, Math.min(contentHeight, safePageHeight));
        float safeTargetWidth = Math.max(1f, targetWidth);

        float widthOccupancy = safeContentWidth / safePageWidth;
        float heightOccupancy = safeContentHeight / safePageHeight;

        int widthDrivenColumns = 1;
        float widthScale = safeTargetWidth / safeContentWidth;
        if (widthScale < MIN_READABLE_WIDTH_SCALE) {
            widthDrivenColumns = (int)Math.ceil(
                    safeContentWidth * MIN_READABLE_WIDTH_SCALE / safeTargetWidth);
        }

        int aspectDrivenColumns = 1;
        boolean substantialContent =
                widthOccupancy >= MIN_WIDTH_OCCUPANCY
                && heightOccupancy >= MIN_HEIGHT_OCCUPANCY;
        if (substantialContent) {
            float aspect = safeContentWidth / safeContentHeight;
            aspectDrivenColumns = (int)Math.ceil(aspect / TARGET_SEGMENT_ASPECT);
        }

        int columns = Math.max(widthDrivenColumns, aspectDrivenColumns);
        return Math.max(1, Math.min(MAX_COLUMNS, columns));
    }

    static boolean shouldUseLandscape(float pageWidth, float pageHeight,
                                      float contentWidth, float contentHeight,
                                      int columns) {
        if (columns <= 1) {
            return pageWidth > pageHeight;
        }
        float segmentWidth = Math.max(1f, contentWidth) / Math.max(1, columns);
        float segmentAspect = segmentWidth / Math.max(1f, contentHeight);
        return segmentAspect >= LANDSCAPE_SEGMENT_ASPECT;
    }

    static float limitUpscale(float requestedScale, float nativeScale) {
        float safeRequested = Math.max(0.0001f, requestedScale);
        float safeNative = Math.max(0.0001f, nativeScale);
        return Math.min(safeRequested, safeNative);
    }
}
