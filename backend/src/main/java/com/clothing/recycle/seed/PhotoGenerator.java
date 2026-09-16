package com.clothing.recycle.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 生成演示用占位照片（纯色 + 衣物简笔画），避免空目录与外链依赖 */
@Component
public class PhotoGenerator {

    private final Path root;

    public PhotoGenerator(@Value("${app.upload.dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir);
        Files.createDirectories(root);
    }

    public String generate(String name, int rgb) throws IOException {
        Path target = root.resolve(name);
        if (Files.exists(target)) return name;
        BufferedImage img = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = new Color(rgb);
        GradientPaint paint = new GradientPaint(0, 0, base.brighter(), 640, 360, base.darker());
        g.setPaint(paint);
        g.fillRect(0, 0, 640, 360);

        // 简化衣物图形
        g.setColor(new Color(255, 255, 255, 210));
        g.fill(new RoundRectangle2D.Double(240, 90, 160, 190, 24, 24)); // 衣身
        Polygon left = new Polygon(new int[]{240, 180, 200, 250}, new int[]{110, 150, 210, 170}, 4);
        Polygon right = new Polygon(new int[]{400, 460, 440, 390}, new int[]{110, 150, 210, 170}, 4);
        g.fill(left);
        g.fill(right);
        g.setColor(base.darker());
        g.fillArc(290, 78, 60, 40, 0, 180); // 领口
        g.setColor(new Color(255, 255, 255, 160));
        g.fillRoundRect(275, 300, 90, 26, 12, 12); // 标签条

        g.dispose();
        ImageIO.write(img, "jpg", target.toFile());
        return name;
    }
}
