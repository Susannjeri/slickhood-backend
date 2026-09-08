package org.pms.silverocean.service.mustache;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.Mustache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RenderService {
    private static final String MUSTACHE_SUFFIX = ".mustache";
    private static final Pattern XHTML_VOID_ELEMENT = Pattern.compile(
            "(?i)<(meta|img|br|hr|input|link)(\\b[^>]*?)(?<!/)>"
    );
    private final Mustache.Compiler mustacheCompiler;
    private final ThreadPoolBeans threadPoolService;
    private PMSThreadPoolExecutorService renderingThreadPool;


    @Autowired
    public RenderService(Mustache.Compiler mustacheCompiler, ThreadPoolBeans threadPoolService) {
        this.mustacheCompiler = mustacheCompiler;
        this.threadPoolService = threadPoolService;
    }

    @PostConstruct
    void init() {
        renderingThreadPool = threadPoolService.cpuExecutorService("PDF-RENDERER-", 1, 1000);
    }

    public String render(String templateName, Object model) {
        try {
            String templatePath = "/templates/" + (templateName.endsWith(MUSTACHE_SUFFIX) ? templateName : templateName + MUSTACHE_SUFFIX);
            InputStream resourceAsStream = getClass().getResourceAsStream(templatePath);
            if (resourceAsStream == null) {
                log.error("Resource not found: {}", templatePath);
                throw new PMSCustomException(ResponseCode.TEMPLATE_NOT_FOUND);
            }
            var reader = new java.io.InputStreamReader(
                    resourceAsStream
            );
            StringWriter writer = new StringWriter();
            mustacheCompiler.compile(reader).execute(model, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render mustache template: " + templateName, e);
        }
    }

    public String renderInline(String template, Object model) {
        try {
            StringWriter writer = new StringWriter();
            mustacheCompiler.compile(template).execute(model, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render inline mustache template", e);
        }
    }

    public void toPdf(String htmlContent, OutputStream outputStream) throws IOException {
        // 5. Stream PDF to OutputStream
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(pdfSafeHtml(htmlContent), null);
        builder.useDefaultPageSize(210, 297, PdfRendererBuilder.PageSizeUnits.MM);
        builder.toStream(outputStream);
        builder.run();
    }

    @Async // Optional: if you want Spring to handle the thread pool
    public CompletableFuture<byte[]> toPdfAsync(String htmlContent) {
        return renderingThreadPool.submit(() -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(pdfSafeHtml(htmlContent), null);
                builder.useDefaultPageSize(210, 297, PdfRendererBuilder.PageSizeUnits.MM);
                builder.toStream(baos);
                builder.run();

                return baos.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate PDF asynchronously", e);
            }
        });
    }

    static String pdfSafeHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "<html><head><meta charset=\"UTF-8\"/></head><body></body></html>";
        }
        String html = htmlContent.strip();
        while (html.startsWith("\uFEFF")) html = html.substring(1).stripLeading();
        if (html.startsWith("ï»¿")) html = html.substring(3).stripLeading();
        if (html.startsWith("```")) {
            int firstLine = html.indexOf('\n');
            if (firstLine >= 0) html = html.substring(firstLine + 1);
            if (html.stripTrailing().endsWith("```")) {
                html = html.stripTrailing();
                html = html.substring(0, html.length() - 3).stripTrailing();
            }
        }
        String lower = html.toLowerCase(Locale.ROOT);
        // OpenHTMLtoPDF parses XHTML through an XML reader. Browsers accept a
        // lowercase HTML5 doctype, but XML requires the DOCTYPE keyword to be
        // uppercase and otherwise fails at line 1, column 3.
        if (lower.startsWith("<!doctype")) {
            html = "<!DOCTYPE" + html.substring("<!doctype".length());
            lower = html.toLowerCase(Locale.ROOT);
        }
        if (!lower.startsWith("<!doctype") && !lower.startsWith("<html")) {
            html = "<html><head><meta charset=\"UTF-8\"/></head><body>" + html + "</body></html>";
        }
        return XHTML_VOID_ELEMENT.matcher(html).replaceAll("<$1$2/>");
    }

}
