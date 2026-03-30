create table if not exists event_store (
    event_id varchar(64) primary key,
    event_type varchar(255) not null,
    aggregate_id varchar(64) not null,
    symbol varchar(32),
    account_id varchar(64),
    trace_id varchar(64) not null,
    occurred_at timestamptz not null,
    payload jsonb not null
);

create index if not exists idx_event_store_aggregate_id on event_store (aggregate_id, occurred_at);
create index if not exists idx_event_store_symbol on event_store (symbol, occurred_at);
