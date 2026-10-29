package jp.local.fax2840usb;

final class DocumentPagePlacement {
    static final class Result {
        final float scale;
        final float offsetX;
        final float offsetY;
        final float renderedWidth;
        final float renderedHeight;

        Result(float scale, float offsetX, float offsetY,
               float renderedWidth, float renderedHeight) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.renderedWidth = renderedWidth;
            this.renderedHeight = renderedHeight;
        }
    }

    private DocumentPagePlacement() {}

    static Result fit(float sourceWidth, float sourceHeight,
                      float sheetWidth, float sheetHeight) {
        float safeSourceWidth = Math.max(1f, sourceWidth);
        float safeSourceHeight = Math.max(1f, sourceHeight);
        float scale = Math.min(
                sheetWidth / safeSourceWidth,
                sheetHeight / safeSourceHeight);
        float renderedWidth = safeSourceWidth * scale;
        float renderedHeight = safeSourceHeight * scale;
        float offsetX = (sheetWidth - renderedWidth) / 2f;
        float offsetY = (sheetHeight - renderedHeight) / 2f;
        return new Result(scale, offsetX, offsetY, renderedWidth, renderedHeight);
    }
}
