package jp.local.fax2840usb;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SharedPdfPreviewActivity extends Activity {
    private TextView status;
    private LinearLayout previewContainer;
    private Button printButton;
    private File sourcePdf;
    private File preparedPdf;
    private int preparedPageCount;
    private final AtomicInteger generation = new AtomicInteger();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        Uri incoming = extractSharedPdf(getIntent());
        if (incoming == null) {
            status.setText("PDFを受け取れませんでした。Googleスプレッドシートで「共有とエクスポート」→「コピーを送信」→「PDF」を選択してください。");
            printButton.setEnabled(false);
            return;
        }
        copyIncomingAndPrepare(incoming);
    }

    private void buildUi() {
        getWindow().setStatusBarColor(AppUi.COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(AppUi.COLOR_BACKGROUND);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, dp(16));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(AppUi.COLOR_BACKGROUND);

        TextView title = new TextView(this);
        title.setText("分割プレビュー");
        AppUi.styleTitle(title);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("実際に印刷されるA4ページを確認してください");
        AppUi.styleBody(subtitle);
        subtitle.setPadding(0, dp(2), 0, dp(4));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout settingsCard = AppUi.card(this);

        TextView modeTitle = new TextView(this);
        modeTitle.setText("ページ分割方式");
        AppUi.styleSectionTitle(modeTitle);
        settingsCard.addView(modeTitle);

        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PageSplitSettings.MODE_LABELS);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        modeSpinner.setSelection(PageSplitSettings.indexOfMode(
                PageSplitSettings.getMode(this)));
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int safe = Math.max(0, Math.min(PageSplitSettings.MODES.length - 1, position));
                int mode = PageSplitSettings.MODES[safe];
                PageSplitSettings.setMode(SharedPdfPreviewActivity.this, mode);
                if (sourcePdf != null) regenerate(mode);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.topMargin = dp(6);
        settingsCard.addView(modeSpinner, spinnerParams);

        status = new TextView(this);
        status.setText("PDFを読み込み中…");
        AppUi.styleBody(status);
        status.setPadding(0, dp(8), 0, 0);
        settingsCard.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AppUi.addCard(root, settingsCard);

        TextView previewTitle = new TextView(this);
        previewTitle.setText("印刷ページ");
        AppUi.styleSectionTitle(previewTitle);
        LinearLayout.LayoutParams previewTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewTitleParams.topMargin = dp(16);
        root.addView(previewTitle, previewTitleParams);

        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.VERTICAL);
        previewContainer.setPadding(0, dp(2), 0, dp(10));

        ScrollView previewScroll = new ScrollView(this);
        previewScroll.setFillViewport(true);
        previewScroll.addView(previewContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(previewScroll, previewParams);

        TextView printHint = new TextView(this);
        printHint.setText("確認後、Androidの印刷画面でFAX-2840を選択します。");
        AppUi.styleBody(printHint);
        printHint.setGravity(Gravity.CENTER);
        printHint.setPadding(0, dp(4), 0, dp(6));
        root.addView(printHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        printButton = new Button(this);
        printButton.setText("印刷へ進む");
        AppUi.stylePrimaryButton(printButton);
        printButton.setEnabled(false);
        printButton.setOnClickListener(v -> openAndroidPrintPreview());
        root.addView(printButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void copyIncomingAndPrepare(Uri uri) {
        printButton.setEnabled(false);
        status.setText("PDFを読み込み中…");

        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "shared-preview");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("一時フォルダーを作成できません");
                }
                File source = new File(dir, "source-" + System.currentTimeMillis() + ".pdf");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(source)) {
                    if (in == null) throw new IOException("共有PDFを開けません");
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                    out.flush();
                }
                sourcePdf = source;
                regenerate(PageSplitSettings.getMode(this));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    status.setText("PDF読み込みエラー: " + safeMessage(e));
                    printButton.setEnabled(false);
                });
            }
        }, "fax2840-shared-copy").start();
    }

    private void regenerate(int mode) {
        File source = sourcePdf;
        if (source == null) return;

        int token = generation.incrementAndGet();
        runOnUiThread(() -> {
            status.setText("分割プレビューを作成中…");
            printButton.setEnabled(false);
            previewContainer.removeAllViews();
        });

        new Thread(() -> {
            try {
                File dir = source.getParentFile();
                File output = new File(dir, "prepared-" + token + ".pdf");
                SplitPdfGenerator.Result result =
                        SplitPdfGenerator.generate(source, output, mode);
                if (token != generation.get()) return;

                preparedPdf = result.file;
                preparedPageCount = result.pageCount;
                renderPreparedPreview(result.file, result.pageCount, token);
            } catch (IOException | RuntimeException e) {
                if (token != generation.get()) return;
                runOnUiThread(() -> {
                    status.setText("分割プレビュー作成エラー: " + safeMessage(e));
                    printButton.setEnabled(false);
                });
            }
        }, "fax2840-preview-generate").start();
    }

    private void renderPreparedPreview(File pdf, int expectedPages, int token) throws IOException {
        List<Bitmap> thumbnails = new ArrayList<>();

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                     pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            for (int i = 0; i < renderer.getPageCount(); i++) {
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    int width = 720;
                    int height = Math.max(1,
                            Math.round(width * page.getHeight() / (float)page.getWidth()));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    Matrix matrix = new Matrix();
                    matrix.setScale(width / (float)page.getWidth(),
                            height / (float)page.getHeight());
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    thumbnails.add(bitmap);
                }
            }
        }

        if (token != generation.get()) {
            for (Bitmap b : thumbnails) b.recycle();
            return;
        }

        runOnUiThread(() -> {
            previewContainer.removeAllViews();

            for (int i = 0; i < thumbnails.size(); i++) {
                LinearLayout pageCard = AppUi.card(this);

                TextView pageLabel = new TextView(this);
                pageLabel.setText("ページ " + (i + 1) + " / " + thumbnails.size());
                pageLabel.setTextColor(AppUi.COLOR_TEXT);
                pageLabel.setTextSize(15f);
                pageLabel.setPadding(0, 0, 0, dp(8));
                pageCard.addView(pageLabel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                ImageView image = new ImageView(this);
                image.setImageBitmap(thumbnails.get(i));
                image.setAdjustViewBounds(true);
                image.setBackground(AppUi.rounded(this,
                        Color.WHITE, 8, AppUi.COLOR_BORDER, 1));
                pageCard.addView(image, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                AppUi.addCard(previewContainer, pageCard);
            }

            preparedPageCount = expectedPages;
            status.setText("分割完了: " + expectedPages + "ページ");
            printButton.setEnabled(preparedPdf != null && preparedPdf.exists());
        });
    }

    private void openAndroidPrintPreview() {
        File pdf = preparedPdf;
        if (pdf == null || !pdf.exists() || preparedPageCount <= 0) {
            status.setText("分割済みPDFがまだ準備できていません。");
            return;
        }

        PrintManager printManager = (PrintManager)getSystemService(PRINT_SERVICE);
        if (printManager == null) {
            status.setText("Android印刷サービスを開けません。");
            return;
        }

        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build();

        printManager.print(
                PreparedPdfPrintAdapter.DOCUMENT_MARKER,
                new PreparedPdfPrintAdapter(pdf, preparedPageCount),
                attrs);
    }

    private static Uri extractSharedPdf(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 33) {
                return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            }
            @SuppressWarnings("deprecation")
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            return uri;
        }
        return intent.getData();
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private int dp(int value) {
        return AppUi.dp(this, value);
    }
}
