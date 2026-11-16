package com.navya;

import static com.navya.TestSupport.input;
import static com.navya.TestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.domain.Conversation;
import com.navya.domain.Listing;
import com.navya.exception.InvalidInputException;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.NotFoundException;
import com.navya.exception.SelfChatException;
import com.navya.service.Principal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatServiceTest {
    AppContext ctx;
    Principal anna, ben, cara;
    Listing listing;

    @BeforeEach void setUp() {
        ctx = TestSupport.context(TestSupport.clock());
        anna = signup(ctx, "Anna");
        ben = signup(ctx, "Ben");
        cara = signup(ctx, "Cara");
        listing = ctx.listings.create(anna, input("Jacket", 500));
    }

    @AfterEach void tearDown() { ctx.close(); }

    @Test
    @DisplayName("TC-36 a buyer starts a chat and gets the same one when asking again")
    void start() {
        Conversation c1 = ctx.chats.start(ben, listing.id());
        assertThat(c1.sellerId()).isEqualTo(anna.userId());
        assertThat(ctx.chats.start(ben, listing.id()).id()).isEqualTo(c1.id());
        assertThat(ctx.chats.start(cara, listing.id()).id()).isNotEqualTo(c1.id());
    }

    @Test
    @DisplayName("TC-37 a seller cannot chat with themselves; sold and missing listings refuse")
    void startRefused() {
        assertThatThrownBy(() -> ctx.chats.start(anna, listing.id())).isInstanceOf(SelfChatException.class);
        ctx.listings.setStatus(anna, listing.id(), "sold");
        assertThatThrownBy(() -> ctx.chats.start(ben, listing.id())).isInstanceOf(ListingNotAvailableException.class);
        assertThatThrownBy(() -> ctx.chats.start(ben, com.navya.util.Ids.newId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ctx.chats.start(ben, "nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("TC-38 messages arrive in order and both people see them")
    void messages() {
        Conversation c = ctx.chats.start(ben, listing.id());
        ctx.chats.send(ben, c.id(), "Is it available?");
        ctx.chats.send(anna, c.id(), "Yes");
        ctx.chats.send(ben, c.id(), "Can we meet at 5?");
        assertThat(ctx.chats.messages(anna, c.id(), 0)).extracting(m -> m.body()).containsExactly("Is it available?", "Yes", "Can we meet at 5?");
        long first = ctx.chats.messages(ben, c.id(), 0).get(0).id();
        assertThat(ctx.chats.messages(ben, c.id(), first)).hasSize(2); // only what is newer
    }

    @Test
    @DisplayName("TC-39 a third person cannot read or write a chat, and cannot tell it exists")
    void strangers() {
        Conversation c = ctx.chats.start(ben, listing.id());
        ctx.chats.send(ben, c.id(), "hi");
        assertThatThrownBy(() -> ctx.chats.messages(cara, c.id(), 0)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ctx.chats.send(cara, c.id(), "hello")).isInstanceOf(NotFoundException.class);
        assertThat(ctx.chats.inbox(cara)).isEmpty();
    }

    @Test
    @DisplayName("TC-40 message length boundaries: empty and 2001 rejected, 2000 accepted")
    void messageBounds() {
        Conversation c = ctx.chats.start(ben, listing.id());
        assertThatThrownBy(() -> ctx.chats.send(ben, c.id(), "")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.chats.send(ben, c.id(), "   ")).isInstanceOf(InvalidInputException.class);
        assertThat(ctx.chats.send(ben, c.id(), "x".repeat(2000)).body()).hasSize(2000);
        assertThatThrownBy(() -> ctx.chats.send(ben, c.id(), "x".repeat(2001))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-41 inbox shows the other person, the listing and the last message, newest chat first")
    void inbox() {
        Conversation c1 = ctx.chats.start(ben, listing.id());
        Listing second = ctx.listings.create(anna, input("Scarf", 100));
        Conversation c2 = ctx.chats.start(cara, second.id());
        ctx.chats.send(ben, c1.id(), "first");
        ctx.chats.send(cara, c2.id(), "second");
        var rows = ctx.chats.inbox(anna);
        assertThat(rows).extracting(r -> r.conversation().id()).containsExactly(c2.id(), c1.id());
        assertThat(rows.get(0).otherName()).isEqualTo("Cara");
        assertThat(rows.get(0).lastBody()).isEqualTo("second");
        assertThat(ctx.chats.inbox(ben)).hasSize(1);
    }

    @Test
    @DisplayName("TC-42 40 buyers-taps at once on 'Message seller' make exactly one chat per buyer")
    void raceStart() throws Exception {
        int n = 40;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int i = 0; i < n; i++) pool.submit(() -> {
            try {
                go.await();
                ids.add(ctx.chats.start(ben, listing.id()).id());
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
        assertThat(ids).hasSize(1);
    }
}
