package org.jeecg.modules.system.util;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * 登录验证码工具类（2026-10 重新设计视觉）。
 *
 * <p>旧实现的问题：105×35 的白底 JPEG、灰色 1px 边框、衬线字体黑字，
 * 外加 200 条"干扰线"（实际是 2px 的随机小线段）——整张图是一层灰噪点，
 * 在店铺端 48px 高的圆角容器里被放大 + 裁切，看起来又糊又脏。</p>
 *
 * <p>新实现：</p>
 * <ul>
 *   <li>2 倍分辨率（224×96，前端按 112×48 显示），Retina/高分屏下不糊；</li>
 *   <li>奶油白渐变底 + 极低透明度的品牌色光斑，和店铺端「珊瑚玫红 × 奶油白」同一套色；</li>
 *   <li>无边框（边框由前端的圆角容器负责，避免"框套框"）；</li>
 *   <li>4 个字符用无衬线粗体、品牌深珊瑚色、逐个轻微旋转 + 随机基线，可读但不呆板；</li>
 *   <li>干扰改为 2~3 条低透明度贝塞尔曲线 + 十几个浅色小点，够防机器识别、又不糊眼睛；</li>
 *   <li>PNG 输出（JPEG 会把文字边缘糊成毛边，且白底噪点更明显）。</li>
 * </ul>
 *
 * <p>安全上保持原有强度：字符仍由 {@code SecureRandom} 生成随机旋转/颜色/曲线形态，
 * 且验证码本身仍由调用方（LoginController）用 RandomUtil 生成并写入 Redis；
 * 本类只负责"把给定的 4 个字符画出来"。</p>
 *
 * @author Cikian
 */
public class RandImageUtil {

    // 静态初始化块，解决无头环境字体配置问题
    static {
        System.setProperty("java.awt.headless", "true");
    }

    public static final String KEY = "JEECG_LOGIN_KEY";

    /** 验证码图片宽度（2x：前端按 112px 显示） */
    private static final int WIDTH = 224;

    /** 验证码图片高度（2x：前端按 48px 显示） */
    private static final int HEIGHT = 96;

    /** 图片格式：PNG 才能保住文字边缘的锐利度 */
    private static final String IMG_FORMAT = "PNG";

    /** base64 图片前缀 */
    private static final String BASE64_PREFIX = "data:image/png;base64,";

    /** 字体大小（2x 下的像素字号）。40 适配 5 个字符的宽度，同时留出上下呼吸空间。 */
    private static final int FONT_SIZE = 40;

    /** 字符水平步长（2x） */
    private static final int CHAR_STEP = 40;

    /** 第一个字符的中心 X（2x） */
    private static final int CHAR_START_X = 24;

    /** 文字基线 Y（2x），让 4 个字符在 96px 高度内视觉居中 */
    private static final int CHAR_BASELINE_Y = 62;

    /** 品牌深珊瑚阶梯：文字在这些颜色里随机取，保证在浅底上的对比度 */
    private static final Color[] TEXT_COLORS = {
            new Color(0xE63E5C),
            new Color(0xD9314F),
            new Color(0xC42F4A),
            new Color(0xB0243D),
    };

    /** 光斑用的品牌浅色（低透明度叠加） */
    private static final Color[] GLOW_COLORS = {
            new Color(0xFF, 0xD7, 0xDE),
            new Color(0xFF, 0xC2, 0xCD),
            new Color(0xFF, 0xE4, 0x7D),
    };

    /** 干扰曲线颜色 */
    private static final Color[] WAVE_COLORS = {
            new Color(0xFF, 0x9A, 0xAC),
            new Color(0xFF, 0x73, 0x8C),
            new Color(0xF5, 0xC2, 0x1F),
    };

    /**
     * 直接通过 response 输出验证码图片。
     *
     * @param response   HTTP 响应对象
     * @param verifyCode 验证码字符串
     * @throws IOException 输出异常
     */
    public static void generate(HttpServletResponse response, String verifyCode) throws IOException {
        if (response == null || verifyCode == null || verifyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }
        try {
            response.setContentType("image/png");
            BufferedImage image = createVerifyCodeImage(verifyCode);
            ImageIO.write(image, IMG_FORMAT, response.getOutputStream());
        } catch (Exception e) {
            throw new IOException("生成验证码图片失败", e);
        }
    }

    /**
     * 生成验证码的 base64 字符串。
     *
     * @param verifyCode 验证码字符串
     * @return data:image/png;base64,... 形式的内联图片
     * @throws IOException 生成异常
     */
    public static String generate(String verifyCode) throws IOException {
        if (verifyCode == null || verifyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        try {
            BufferedImage image = createVerifyCodeImage(verifyCode);
            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                ImageIO.write(image, IMG_FORMAT, byteStream);
                String base64 = Base64.getEncoder().encodeToString(byteStream.toByteArray()).trim();
                base64 = base64.replaceAll("[\r\n]", "");
                return BASE64_PREFIX + base64;
            }
        } catch (Exception e) {
            throw new IOException("生成验证码base64失败", e);
        }
    }

    /**
     * 创建验证码图像。
     *
     * @param verifyCode 验证码字符串
     * @return 验证码图像
     */
    private static BufferedImage createVerifyCodeImage(String verifyCode) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = null;
        try {
            graphics = image.createGraphics();
            setupRenderingHints(graphics);
            drawBackground(graphics);

            SecureRandom random = createSecureRandom();
            drawGlowSpots(graphics, random);
            drawWaveLines(graphics, random);
            drawDots(graphics, random);
            drawVerifyCodeText(graphics, verifyCode, random);
        } catch (Exception e) {
            // 绘制失败时退回简单的可读图像，至少不让登录流程抛 500
            return createErrorImage(verifyCode);
        } finally {
            if (graphics != null) {
                graphics.dispose();
            }
        }
        return image;
    }

    /**
     * 设置图形渲染质量。
     */
    private static void setupRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * 奶油白 → 纯白的柔和渐变底。
     * 与店铺端页面底色 --color-cream(#FFF6F2) 同源，避免验证码像一块"贴上去的白板"。
     */
    private static void drawBackground(Graphics2D graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(0xFF, 0xF7, 0xF3), WIDTH, HEIGHT, Color.WHITE));
            g.fillRect(0, 0, WIDTH, HEIGHT);
        } finally {
            g.dispose();
        }
    }

    /**
     * 2 个品牌色光斑：用多层低透明度同心圆模拟柔和扩散（AWT 没有模糊，靠叠加就够了）。
     * 透明度控制在 8~12，肉眼只看到"暖调"，不会和字符抢注意力。
     */
    private static void drawGlowSpots(Graphics2D graphics, SecureRandom random) {
        drawSoftCircle(graphics, random.nextInt(WIDTH / 2), random.nextInt(HEIGHT), 62, pick(random, GLOW_COLORS), 12);
        drawSoftCircle(graphics, WIDTH / 2 + random.nextInt(WIDTH / 2), random.nextInt(HEIGHT), 54, pick(random, GLOW_COLORS), 10);
    }

    private static void drawSoftCircle(Graphics2D graphics, int cx, int cy, int radius, Color color, int alpha) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int rings = 5;
            for (int i = 0; i < rings; i++) {
                int r = radius - i * (radius / (rings + 2));
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(4, alpha - i * 2)));
                g.fillOval(cx - r, cy - r, r * 2, r * 2);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * 2~3 条低透明度贝塞尔曲线。
     * 旧实现是 200 条 2px 小线段（等于撒了一层灰），这里改成少量长曲线：
     * 机器识别时要先拟合曲线，干扰成本没降多少，人眼却清爽很多。
     */
    private static void drawWaveLines(Graphics2D graphics, SecureRandom random) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int count = 3 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Color base = pick(random, WAVE_COLORS);
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 78));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int startY = 12 + random.nextInt(HEIGHT - 24);
                int ctrlX = random.nextInt(WIDTH);
                int ctrlY = random.nextInt(HEIGHT);
                int endY = 12 + random.nextInt(HEIGHT - 24);
                g.draw(new QuadCurve2D.Float(0, startY, ctrlX, ctrlY, WIDTH, endY));
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * 十几个浅色小点：替代"满屏噪点"，只保留最低限度的抗分割干扰。
     */
    private static void drawDots(Graphics2D graphics, SecureRandom random) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            for (int i = 0; i < 16; i++) {
                Color base = pick(random, GLOW_COLORS);
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90));
                int r = 1 + random.nextInt(2);
                int x = random.nextInt(WIDTH - 4) + 2;
                int y = random.nextInt(HEIGHT - 4) + 2;
                g.fillOval(x, y, r * 2, r * 2);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * 绘制验证码文本：每个字符独立字体、独立旋转（±14°）、轻微剪切与随机基线。
     *
     * <p>这是"提高 OCR 成本"的一部分（真正的抗 OCR 方案是行为验证码/托管验证码，
     * 见 docs）：单字符随机字体 + 剪切 + 旋转会让通用 OCR 的字符分割和模板匹配
     * 明显变差，但**不能指望它挡住针对性训练**。安全边界最终由
     * 「一次性 + 5 次失败作废 + IP 限流 + 邮箱验证码尝试上限」承担。</p>
     */
    private static void drawVerifyCodeText(Graphics2D graphics, String verifyCode, SecureRandom random) {
        String[] fontFamilies = {Font.SANS_SERIF, "Dialog", Font.MONOSPACED};
        int length = verifyCode.length();
        for (int i = 0; i < length; i++) {
            char character = verifyCode.charAt(i);
            String text = String.valueOf(character);

            int centerX = CHAR_START_X + i * CHAR_STEP + random.nextInt(5) - 2;
            int baselineY = CHAR_BASELINE_Y + random.nextInt(9) - 4;
            double angle = Math.toRadians(random.nextInt(29) - 14);
            double shear = (random.nextDouble() - 0.5) * 0.36;
            Color color = pick(random, TEXT_COLORS);
            Font font = new Font(fontFamilies[random.nextInt(fontFamilies.length)], Font.BOLD,
                    FONT_SIZE + random.nextInt(5) - 2);

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.rotate(angle, centerX, baselineY);
                g.shear(shear, 0);
                g.setFont(font);
                FontMetrics metrics = g.getFontMetrics();
                int textWidth = metrics.stringWidth(text);

                // 极淡的暖色投影：让字从奶油底上"浮"起来，但不产生脏边
                g.setColor(new Color(31, 26, 28, 26));
                g.drawString(text, centerX - textWidth / 2 + 2, baselineY + 2);

                g.setColor(color);
                g.drawString(text, centerX - textWidth / 2, baselineY);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * 选择无衬线粗体：旧实现优先 SERIF，是"老式验证码"观感的主要来源之一。
     * 逻辑字体 SANS_SERIF 在所有平台都可用，无需安装额外字体。
     */
    private static Font createSafeFont() {
        String[] candidates = {Font.SANS_SERIF, "Dialog", "Microsoft YaHei UI", "Segoe UI", Font.MONOSPACED};
        for (String name : candidates) {
            try {
                Font font = new Font(name, Font.BOLD, FONT_SIZE);
                if (font.getFamily() != null) {
                    return font;
                }
            } catch (Exception ignored) {
                // 继续尝试下一个字体
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
    }

    /**
     * 创建错误图像（当正常绘制失败时使用）：保持同一尺寸与浅底，避免前端布局跳动。
     */
    private static BufferedImage createErrorImage(String verifyCode) {
        BufferedImage errorImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = null;
        try {
            graphics = errorImage.createGraphics();
            setupRenderingHints(graphics);
            graphics.setColor(new Color(0xFF, 0xF7, 0xF3));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
            graphics.setColor(new Color(0xC4, 0x2F, 0x4A));
            String text = verifyCode == null ? "" : verifyCode;
            int x = 16;
            for (int i = 0; i < text.length(); i++) {
                graphics.drawString(String.valueOf(text.charAt(i)), x, 60);
                x += 40;
            }
        } finally {
            if (graphics != null) {
                graphics.dispose();
            }
        }
        return errorImage;
    }

    /**
     * 创建安全的随机数生成器。
     */
    private static SecureRandom createSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom();
        }
    }

    private static <T> T pick(Random random, T[] values) {
        return values[random.nextInt(values.length)];
    }
}
