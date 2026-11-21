# Data requests runbook (internal)

How the Grievance Officer handles a request to see, correct or erase a member's data, as promised in the Privacy Notice. Not served on the site.

## Steps

1. The request must come from the email address on the account. If it does not, ask the member to write from that address. Do not act on any other request.
2. Record the request and give a reference number. Acknowledge within 24 hours.
3. Run the matching procedure below. Finish within 15 days.
4. Reply to the member with what was done and what is kept (the Privacy Notice, sections 6 and 7).
5. Keep the request and the reply for one year after closing.

All SQL runs in `psql` against the managed database, with the member's id in the variable `id`:

```
psql "host=$DB_HOST user=navya dbname=navya sslmode=require" -v id=<member id>
```

The member id comes from `SELECT id FROM users WHERE lower(email) = lower('<email>');`.

## Export (access request)

Gives the member a JSON copy of their data. The password hash is not included.

```sql
SELECT json_build_object(
  'account', (SELECT row_to_json(u) FROM (SELECT id, email, name, city, created_at, consented_at, terms_version FROM users WHERE id = :'id') u),
  'listings', (SELECT coalesce(json_agg(l), '[]') FROM listings l WHERE seller_id = :'id'),
  'photos', (SELECT coalesce(json_agg(i), '[]') FROM listing_images i WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = :'id')),
  'favourites', (SELECT coalesce(json_agg(f), '[]') FROM favourites f WHERE user_id = :'id'),
  'conversations', (SELECT coalesce(json_agg(c), '[]') FROM conversations c WHERE buyer_id = :'id' OR seller_id = :'id'),
  'messages', (SELECT coalesce(json_agg(m), '[]') FROM messages m WHERE conversation_id IN (SELECT id FROM conversations WHERE buyer_id = :'id' OR seller_id = :'id')),
  'reports', (SELECT coalesce(json_agg(r), '[]') FROM reports r WHERE reporter_id = :'id'),
  'orders', (SELECT coalesce(json_agg(o), '[]') FROM orders o WHERE user_id = :'id')
);
```

## Erasure

First delete the member's photos from the bucket (the keys start with the member id):

```
aws s3 rm s3://<bucket>/<member id>/ --recursive
```

Then run this in one transaction. It removes the member's listings (and with them their photos' rows, favourites by others, chats about them and reports on them), their chats and messages, their favourites and their reports. It keeps the account row with neutral text, so boost orders stay as payment records without a name or email. The password hash is replaced by a hash of a random password nobody knows, so the account cannot be logged into.

```sql
BEGIN;
DELETE FROM conversations WHERE buyer_id = :'id' OR seller_id = :'id';
DELETE FROM reports WHERE reporter_id = :'id';
DELETE FROM favourites WHERE user_id = :'id';
DELETE FROM listings WHERE seller_id = :'id';
UPDATE users
   SET email = 'deleted-' || id || '@deleted.invalid', name = 'Deleted member', city = '',
       password_hash = '$2a$10$8hedvMlXjM4JevAUOOTLzuHNMQMH0L98GkRznn.KVzgUhfKYkN2pK',
       consented_at = NULL, terms_version = NULL
 WHERE id = :'id' AND role = 'MEMBER';
COMMIT;
```

The update must change exactly one row. If it says `UPDATE 0`, the id is wrong or belongs to an admin: roll back and check.

What stays: boost orders (without the member's details), the audit log, and copies in the database's automatic backups until they expire.

## Correction

Change the field the member names, for example `UPDATE users SET name = '<new name>' WHERE id = :'id';`, and tell them it is done.

## Tested

Run on 2026-11-25 against a scratch copy of the live database (restored from a dump, then dropped): see `../../5-Cloud-Computing/11-demo-deployment-record.md`.
