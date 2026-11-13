package com.navya;

import com.navya.domain.User;
import com.navya.service.ListingService;
import com.navya.service.Principal;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Demo data for local runs and the demo. Runs once: if the demo seller exists it does nothing. */
public final class Seeder {
    private static final Logger log = LoggerFactory.getLogger(Seeder.class);

    private record Item(String title, long price, String category, String condition, String size, String city, String text, Color color) {}

    private Seeder() {}

    public static void run(AppContext ctx, String password) {
        if (password == null || password.length() < 8) {
            byte[] b = new byte[9];
            new SecureRandom().nextBytes(b);
            password = HexFormat.of().formatHex(b);
            log.warn("SEED_PASSWORD not set (needs 8+ characters). Demo accounts use this one-time password: {}", password);
        }
        List<com.navya.domain.Member> sellers;
        try {
            sellers = List.of(ctx.auth.signup("aanya@navya.test", "Aanya", "Pune", password),
                    ctx.auth.signup("rohan@navya.test", "Rohan", "Mumbai", password),
                    ctx.auth.signup("meera@navya.test", "Meera", "Bengaluru", password));
        } catch (com.navya.exception.DuplicateEmailException e) {
            log.info("Seed data already present, skipping");
            return;
        }
        ctx.auth.ensureAdmin("admin@navya.test", password);
        List<Item> items = List.of(
                new Item("Denim jacket", 900, "Clothes", "Good", "M", "Pune", "Light wash, worn a few times. No tears.", new Color(0x5B7BA0)),
                new Item("White canvas sneakers", 650, "Shoes", "Like new", "UK 8", "Pune", "Worn twice. Cleaned and boxed.", new Color(0xD9D4C7)),
                new Item("Black tote bag", 1200, "Bags", "Good", "", "Mumbai", "Fits a 14 inch laptop. Zip works, one small mark inside.", new Color(0x2B2B2B)),
                new Item("Cotton kurta, indigo", 450, "Clothes", "Like new", "L", "Mumbai", "Block print. Washed once.", new Color(0x34437A)),
                new Item("Leather belt", 300, "Accessories", "Fair", "32", "Bengaluru", "Brown, some wear on the buckle.", new Color(0x7A4B2A)),
                new Item("Running shoes", 1500, "Shoes", "Good", "UK 9", "Bengaluru", "About 200 km on them. Good grip left.", new Color(0xB5412F)),
                new Item("Wool scarf", 350, "Accessories", "New with tags", "", "Pune", "Grey, never worn. Gift I did not need.", new Color(0x8A8D91)),
                new Item("Floral summer dress", 800, "Clothes", "Good", "S", "Mumbai", "Cotton, knee length, pockets.", new Color(0xC98B9B)));
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            User u = sellers.get(i % 3);
            Principal p = new Principal(u.id, u.role());
            List<String> images = List.of();
            try {
                images = List.of(ctx.listings.saveImage(p, swatch(it.color())));
            } catch (RuntimeException e) {
                log.warn("Could not make a demo photo: {}", e.getMessage());
            }
            ctx.listings.create(p, new ListingService.Input(it.title(), it.price(), it.category(), it.condition(), it.size(), it.city(), it.text(), images));
        }
        log.info("Seeded 3 members, 1 admin and {} listings", items.size());
    }

    /** A plain coloured JPEG, so demo listings have a photo without copying anyone's pictures. */
    private static byte[] swatch(Color c) {
        BufferedImage img = new BufferedImage(400, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, 400, 500);
        g.setColor(c.brighter());
        g.fillOval(100, 150, 200, 200);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
