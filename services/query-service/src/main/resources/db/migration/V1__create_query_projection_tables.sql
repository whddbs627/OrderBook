create table if not exists order_query_view (
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

create table if not exists balance_query_view (
    account_id varchar(64) primary key,
    cash_balance numeric(19, 4) not null,
    updated_at timestamptz not null
);

create table if not exists position_query_view (
    account_id varchar(64) not null,
    symbol varchar(32) not null,
    quantity bigint not null,
    primary key (account_id, symbol)
);

create table if not exists trade_query_view (
    trade_id varchar(64) primary key,
    symbol varchar(32) not null,
    buy_order_id varchar(64) not null,
    sell_order_id varchar(64) not null,
    buy_account_id varchar(64) not null,
    sell_account_id varchar(64) not null,
    price numeric(19, 4) not null,
    quantity bigint not null,
    occurred_at timestamptz not null
);

create index if not exists idx_trade_query_view_symbol on trade_query_view (symbol, occurred_at desc);
