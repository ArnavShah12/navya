-- Navya schema. Safe to run on every start: every statement is idempotent.
-- Ids are UUID strings made by the app. Money is whole paise.

CREATE TABLE IF NOT EXISTS users (
    id            TEXT PRIMARY KEY,
    email         TEXT NOT NULL,
    name          TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    city          TEXT NOT NULL DEFAULT '',
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'ADMIN')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Two sign-ups with the same email can never both succeed, even on two app instances.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq ON users (lower(email));
-- Consent record: when the member confirmed they are 18 or older and accepted the Terms and Privacy Notice, and which version.
ALTER TABLE users ADD COLUMN IF NOT EXISTS consented_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_version TEXT;

CREATE TABLE IF NOT EXISTS listings (
    id           TEXT PRIMARY KEY,
    seller_id    TEXT NOT NULL REFERENCES users (id),
    title        TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
    price_paise  BIGINT NOT NULL CHECK (price_paise BETWEEN 0 AND 1000000000),
    category     TEXT NOT NULL CHECK (category IN ('Clothes', 'Shoes', 'Bags', 'Accessories')),
    condition    TEXT NOT NULL CHECK (condition IN ('New with tags', 'Like new', 'Good', 'Fair')),
    size         TEXT NOT NULL DEFAULT '',
    city         TEXT NOT NULL,
    description  TEXT NOT NULL CHECK (char_length(description) BETWEEN 1 AND 2000),
    status       TEXT NOT NULL DEFAULT 'available' CHECK (status IN ('available', 'sold', 'removed', 'expired')),
    boosted_until TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS listings_status_created_ix ON listings (status, created_at DESC);
CREATE INDEX IF NOT EXISTS listings_seller_ix ON listings (seller_id);

CREATE TABLE IF NOT EXISTS listing_images (
    listing_id TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    position   SMALLINT NOT NULL CHECK (position BETWEEN 0 AND 3),
    image_key  TEXT NOT NULL,
    PRIMARY KEY (listing_id, position)
);

CREATE TABLE IF NOT EXISTS favourites (
    user_id    TEXT NOT NULL REFERENCES users (id),
    listing_id TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, listing_id)
);

-- One chat per buyer and listing. The unique constraint stops two simultaneous taps making two chats.
CREATE TABLE IF NOT EXISTS conversations (
    id         TEXT PRIMARY KEY,
    listing_id TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    buyer_id   TEXT NOT NULL REFERENCES users (id),
    seller_id  TEXT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (listing_id, buyer_id),
    CHECK (buyer_id <> seller_id)
);
CREATE INDEX IF NOT EXISTS conversations_buyer_ix ON conversations (buyer_id);
CREATE INDEX IF NOT EXISTS conversations_seller_ix ON conversations (seller_id);

CREATE TABLE IF NOT EXISTS messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       TEXT NOT NULL REFERENCES users (id),
    body            TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS messages_conv_ix ON messages (conversation_id, id);

CREATE TABLE IF NOT EXISTS reports (
    id          TEXT PRIMARY KEY,
    listing_id  TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    reporter_id TEXT NOT NULL REFERENCES users (id),
    reason      TEXT NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
    status      TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'actioned', 'dismissed')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (listing_id, reporter_id)
);

-- A paid boost. The listing is kept as history even if the listing row is deleted.
CREATE TABLE IF NOT EXISTS orders (
    id              TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users (id),
    listing_id      TEXT REFERENCES listings (id) ON DELETE SET NULL,
    amount_paise    BIGINT NOT NULL CHECK (amount_paise >= 0),
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'failed')),
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS orders_user_ix ON orders (user_id, created_at DESC);
