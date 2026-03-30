create table if not exists orders (
    order_id varchar(64) primary key,
    account_id varchar(64) not null,
    client_order_id varchar(64) not null unique,
    symbol varchar(32) not null,
    side varchar(16) not null,
    price numeric(19, 4) not null,
    quantity bigint not null,
    filled_quantity bigint not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_orders_account_id on orders (account_id);
create index if not exists idx_orders_symbol on orders (symbol);

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
