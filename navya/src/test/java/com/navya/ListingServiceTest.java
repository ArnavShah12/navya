package com.navya;

import static com.navya.TestSupport.input;
import static com.navya.TestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.domain.Listing;
import com.navya.exception.ForbiddenException;
import com.navya.exception.InvalidInputException;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.NotFoundException;
import com.navya.exception.TooManyImagesException;
import com.navya.service.ListingService;
import com.navya.service.Principal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingServiceTest {
    MutableClock clock;
    AppContext ctx;
    Principal anna, ben;

    @BeforeEach void setUp() {
        clock = TestSupport.clock();
        ctx = TestSupport.context(clock);
        anna = signup(ctx, "Anna");
        ben = signup(ctx, "Ben");
    }

    @AfterEach void tearDown() { ctx.close(); }

    private ListingService.Query q(String text) { return new ListingService.Query(text, null, null, null, null, false, null, 0); }

    @Test
    @DisplayName("TC-21 create a listing and read it back")
    void create() {
        Listing l = ctx.listings.create(anna, input("Denim jacket", 900));
        assertThat(l.pricePaise()).isEqualTo(90_000);
        assertThat(l.status()).isEqualTo("available");
        assertThat(ctx.listings.get(null, l.id()).title()).isEqualTo("Denim jacket");
    }

    @Test
    @DisplayName("TC-22 create rejects a bad title, price, category and condition")
    void createInvalid() {
        assertThatThrownBy(() -> ctx.listings.create(anna, input("", 100))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, input("a".repeat(81), 100))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, input("x", -1))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, new ListingService.Input("x", null, "Clothes", "Good", "", "Pune", "d", List.of()))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, new ListingService.Input("x", 1L, "Cars", "Good", "", "Pune", "d", List.of()))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, new ListingService.Input("x", 1L, "Shoes", "Broken", "", "Pune", "d", List.of()))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, new ListingService.Input("x", 1L, "Shoes", "Good", "", "", "d", List.of()))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.create(anna, null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-23 price 0 and the title at 80 characters are accepted (boundary)")
    void boundariesOk() {
        assertThat(ctx.listings.create(anna, input("a".repeat(80), 0)).pricePaise()).isZero();
    }

    @Test
    @DisplayName("TC-24 four photos are allowed, five are not")
    void imageLimit() {
        List<String> keys = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) keys.add(ctx.listings.saveImage(anna, TestSupport.jpeg()));
        var four = new ListingService.Input("x", 1L, "Shoes", "Good", "", "Pune", "d", keys.subList(0, 4));
        assertThat(ctx.listings.create(anna, four).images()).hasSize(4);
        var five = new ListingService.Input("x", 1L, "Shoes", "Good", "", "Pune", "d", keys);
        assertThatThrownBy(() -> ctx.listings.create(anna, five)).isInstanceOf(TooManyImagesException.class);
    }

    @Test
    @DisplayName("TC-25 photo upload accepts a JPEG only and caps the size")
    void imageUpload() {
        assertThat(ctx.listings.saveImage(anna, TestSupport.jpeg())).startsWith(anna.userId() + "/").endsWith(".jpg");
        assertThatThrownBy(() -> ctx.listings.saveImage(anna, "<script>alert(1)</script>".getBytes())).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.listings.saveImage(anna, new byte[0])).isInstanceOf(InvalidInputException.class);
        byte[] big = new byte[ListingService.MAX_IMAGE_BYTES + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> ctx.listings.saveImage(anna, big)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-26 a listing cannot use another member's photo")
    void foreignImage() {
        String benKey = ctx.listings.saveImage(ben, TestSupport.jpeg());
        var in = new ListingService.Input("x", 1L, "Shoes", "Good", "", "Pune", "d", List.of(benKey));
        assertThatThrownBy(() -> ctx.listings.create(anna, in)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-27 only the seller can edit; the edit changes the fields and drops removed photos")
    void edit() {
        String k1 = ctx.listings.saveImage(anna, TestSupport.jpeg()), k2 = ctx.listings.saveImage(anna, TestSupport.jpeg());
        Listing l = ctx.listings.create(anna, new ListingService.Input("Old", 100L, "Shoes", "Good", "", "Pune", "d", List.of(k1, k2)));
        assertThatThrownBy(() -> ctx.listings.update(ben, l.id(), input("Hacked", 1))).isInstanceOf(ForbiddenException.class);
        Listing u = ctx.listings.update(anna, l.id(), new ListingService.Input("New", 200L, "Shoes", "Fair", "9", "Mumbai", "d2", List.of(k2)));
        assertThat(u.title()).isEqualTo("New");
        assertThat(u.pricePaise()).isEqualTo(20_000);
        assertThat(u.images()).containsExactly(k2);
        assertThat(ctx.storage.exists(k1)).isFalse(); // removed photo is deleted from storage
        assertThat(ctx.storage.exists(k2)).isTrue();
    }

    @Test
    @DisplayName("TC-28 mark sold and available again; only the seller can")
    void status() {
        Listing l = ctx.listings.create(anna, input("x", 1));
        assertThatThrownBy(() -> ctx.listings.setStatus(ben, l.id(), "sold")).isInstanceOf(ForbiddenException.class);
        assertThat(ctx.listings.setStatus(anna, l.id(), "sold").status()).isEqualTo("sold");
        assertThat(ctx.listings.setStatus(anna, l.id(), "available").status()).isEqualTo("available");
        assertThatThrownBy(() -> ctx.listings.setStatus(anna, l.id(), "removed")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-29 delete removes the listing and its photos; a stranger cannot delete")
    void delete() {
        String k = ctx.listings.saveImage(anna, TestSupport.jpeg());
        Listing l = ctx.listings.create(anna, new ListingService.Input("x", 1L, "Shoes", "Good", "", "Pune", "d", List.of(k)));
        assertThatThrownBy(() -> ctx.listings.delete(ben, l.id())).isInstanceOf(ForbiddenException.class);
        ctx.listings.delete(anna, l.id());
        assertThatThrownBy(() -> ctx.listings.get(null, l.id())).isInstanceOf(NotFoundException.class);
        assertThat(ctx.storage.exists(k)).isFalse();
    }

    @Test
    @DisplayName("TC-30 search: every word must match, case-insensitive, across fields")
    void searchWords() {
        ctx.listings.create(anna, new ListingService.Input("Blue denim jacket", 900L, "Clothes", "Good", "M", "Pune", "Warm", List.of()));
        ctx.listings.create(anna, new ListingService.Input("Red scarf", 200L, "Accessories", "Like new", "", "Mumbai", "Wool denim blend", List.of()));
        assertThat(ctx.listings.search(null, q("denim")).total()).isEqualTo(2);
        assertThat(ctx.listings.search(null, q("DENIM jacket")).items()).extracting(Listing::title).containsExactly("Blue denim jacket");
        assertThat(ctx.listings.search(null, q("mumbai")).items()).extracting(Listing::title).containsExactly("Red scarf");
        assertThat(ctx.listings.search(null, q("nothing-like-this")).total()).isZero();
    }

    @Test
    @DisplayName("TC-31 search treats % and quotes as plain text, not as SQL or wildcards")
    void searchSafe() {
        ctx.listings.create(anna, input("Plain shirt", 100));
        assertThat(ctx.listings.search(null, q("%")).total()).isZero();
        assertThat(ctx.listings.search(null, q("_")).total()).isZero();
        assertThat(ctx.listings.search(null, q("'; DROP TABLE listings; --")).total()).isZero();
        assertThat(ctx.listings.search(null, q("shirt")).total()).isEqualTo(1); // table still there
    }

    @Test
    @DisplayName("TC-32 filters: category, city, price range with both bounds inclusive")
    void filters() {
        ctx.listings.create(anna, new ListingService.Input("A", 100L, "Shoes", "Good", "", "Pune", "d", List.of()));
        ctx.listings.create(anna, new ListingService.Input("B", 500L, "Shoes", "Good", "", "Mumbai", "d", List.of()));
        ctx.listings.create(anna, new ListingService.Input("C", 900L, "Bags", "Good", "", "Pune", "d", List.of()));
        assertThat(ctx.listings.search(null, new ListingService.Query(null, "Shoes", null, null, null, false, null, 0)).total()).isEqualTo(2);
        assertThat(ctx.listings.search(null, new ListingService.Query(null, null, "pun", null, null, false, null, 0)).total()).isEqualTo(2);
        assertThat(ctx.listings.search(null, new ListingService.Query(null, null, null, 100L, 500L, false, null, 0)).items()).extracting(Listing::title).containsExactlyInAnyOrder("A", "B");
        assertThat(ctx.listings.search(null, new ListingService.Query(null, null, null, 501L, 899L, false, null, 0)).total()).isZero();
    }

    @Test
    @DisplayName("TC-33 sort by price both ways, newest first by default, paging")
    void sortAndPage() {
        for (int i = 1; i <= 30; i++) ctx.listings.create(anna, input("Item " + i, i * 10));
        var low = ctx.listings.search(null, new ListingService.Query(null, null, null, null, null, false, "low", 0));
        assertThat(low.items().get(0).pricePaise()).isEqualTo(1_000);
        assertThat(low.items()).hasSize(ListingService.PAGE_SIZE);
        assertThat(low.total()).isEqualTo(30);
        var high = ctx.listings.search(null, new ListingService.Query(null, null, null, null, null, false, "high", 0));
        assertThat(high.items().get(0).pricePaise()).isEqualTo(30_000);
        var page2 = ctx.listings.search(null, new ListingService.Query(null, null, null, null, null, false, "low", 1));
        assertThat(page2.items()).hasSize(6);
    }

    @Test
    @DisplayName("TC-34 saved items: toggle, list, filter; login required for the filter")
    void favourites() {
        Listing l = ctx.listings.create(anna, input("Fav", 1));
        ctx.listings.create(anna, input("Other", 1));
        assertThat(ctx.listings.toggleFavourite(ben, l.id())).isTrue();
        assertThat(ctx.listings.favouriteIds(ben)).containsExactly(l.id());
        assertThat(ctx.listings.search(ben, new ListingService.Query(null, null, null, null, null, true, null, 0)).items()).extracting(Listing::title).containsExactly("Fav");
        assertThatThrownBy(() -> ctx.listings.search(null, new ListingService.Query(null, null, null, null, null, true, null, 0))).isInstanceOf(ForbiddenException.class);
        assertThat(ctx.listings.toggleFavourite(ben, l.id())).isFalse();
        assertThat(ctx.listings.favouriteIds(ben)).isEmpty();
    }

    @Test
    @DisplayName("TC-35 listings older than the limit expire; the seller still sees them, others do not")
    void expiry() {
        Listing l = ctx.listings.create(anna, input("Old one", 1));
        clock.advance(Duration.ofDays(61));
        assertThat(ctx.listings.expireOld(60)).isEqualTo(1);
        assertThat(ctx.listings.expireOld(60)).isZero(); // running it again changes nothing
        assertThat(ctx.listings.search(null, q("old")).total()).isZero();
        assertThatThrownBy(() -> ctx.listings.get(ben, l.id())).isInstanceOf(NotFoundException.class);
        assertThat(ctx.listings.get(anna, l.id()).status()).isEqualTo("expired");
        assertThat(ctx.listings.mine(anna)).hasSize(1);
        assertThatThrownBy(() -> ctx.listings.update(anna, l.id(), input("Edit", 1))).isInstanceOf(ListingNotAvailableException.class);
    }

    @Test
    @DisplayName("Malformed ids are 'not found', not a database error")
    void badIds() {
        assertThatThrownBy(() -> ctx.listings.get(null, "1 OR 1=1")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ctx.listings.bySeller("x")).isInstanceOf(NotFoundException.class);
    }
}
