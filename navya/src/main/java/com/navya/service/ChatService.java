package com.navya.service;

import com.navya.domain.Conversation;
import com.navya.domain.Listing;
import com.navya.domain.Message;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.NotFoundException;
import com.navya.exception.SelfChatException;
import com.navya.repo.ChatRepo;
import com.navya.repo.ListingRepo;
import com.navya.util.Ids;
import com.navya.util.Validate;
import com.navya.worker.Notifier;
import java.util.List;

/** One chat per buyer and listing. Only the two people in it can read or write. */
public final class ChatService {
    private final ChatRepo chats;
    private final ListingRepo listings;
    private final Notifier notifier;
    private final Metrics metrics;

    public ChatService(ChatRepo chats, ListingRepo listings, Notifier notifier, Metrics metrics) {
        this.chats = chats;
        this.listings = listings;
        this.notifier = notifier;
        this.metrics = metrics;
    }

    public Conversation start(Principal p, String listingId) {
        Listing l = Ids.valid(listingId) ? listings.get(listingId).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        if (l.sellerId().equals(p.userId())) throw new SelfChatException("This is your own listing");
        if (!Listing.AVAILABLE.equals(l.status())) throw new ListingNotAvailableException("This item is no longer available");
        return chats.findOrCreate(Ids.newId(), listingId, p.userId(), l.sellerId());
    }

    public List<ChatRepo.InboxRow> inbox(Principal p) { return chats.inbox(p.userId()); }

    /** The conversation, if the caller is one of its two people. A stranger gets "not found", not "forbidden", so ids reveal nothing. */
    public Conversation open(Principal p, String id) {
        Conversation c = Ids.valid(id) ? chats.get(id).orElse(null) : null;
        if (c == null || !c.has(p.userId())) throw new NotFoundException("Chat not found");
        return c;
    }

    public List<Message> messages(Principal p, String id, long afterId) {
        open(p, id);
        return chats.messages(id, afterId);
    }

    public Message send(Principal p, String id, String body) {
        Conversation c = open(p, id);
        String text = Validate.text(body, "Message", 1, 2000);
        Message m = chats.add(id, p.userId(), text);
        metrics.messagesSent.incrementAndGet();
        notifier.send(c.otherOf(p.userId()), "new message in chat " + id);
        return m;
    }
}
