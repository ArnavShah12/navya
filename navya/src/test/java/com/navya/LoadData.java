package com.navya;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** Adds N filler listings to a running database so search can be measured on a realistic size. Args: jdbcUrl count. */
public final class LoadData {
    public static void main(String[] args) throws Exception {
        int n = Integer.parseInt(args[1]);
        try (Connection c = DriverManager.getConnection(args[0]); Statement st = c.createStatement()) {
            st.execute("DELETE FROM listings WHERE description LIKE 'Filler listing%'");
            st.execute("INSERT INTO listings (id, seller_id, title, price_paise, category, condition, size, city, description, created_at) "
                    + "SELECT gen_random_uuid()::text, (SELECT id FROM users ORDER BY created_at LIMIT 1), "
                    + "(ARRAY['Denim jacket','Canvas shoes','Tote bag','Cotton kurta','Leather belt','Running shoes','Wool scarf','Summer dress'])[1 + g % 8] || ' #' || g, "
                    + "(100 + g % 3000) * 100, (ARRAY['Clothes','Shoes','Bags','Accessories'])[1 + g % 4], (ARRAY['New with tags','Like new','Good','Fair'])[1 + g % 4], 'M', "
                    + "(ARRAY['Pune','Mumbai','Bengaluru','Delhi','Chennai'])[1 + g % 5], 'Filler listing number ' || g || ' for load testing, worn a few times.', now() - (g || ' minutes')::interval "
                    + "FROM generate_series(1, " + n + ") g ON CONFLICT DO NOTHING");
            System.out.println("added " + n + " listings");
        }
    }
}
