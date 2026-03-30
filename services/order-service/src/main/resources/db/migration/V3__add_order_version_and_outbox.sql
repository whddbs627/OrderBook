alter table orders
    add column if not exists version bigint not null default 0;

create table if not exists outbox_events (
    outbox_id varchar(64) primary key,
    topic varchar(128) not null,
    event_key varchar(128) not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz
);

create index if not exists idx_outbox_events_unpublished
    on outbox_events (published_at, created_at);
