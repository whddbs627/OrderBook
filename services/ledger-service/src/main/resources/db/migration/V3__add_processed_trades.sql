create table if not exists processed_trades (
    trade_id varchar(64) primary key,
    trace_id varchar(64) not null,
    occurred_at timestamptz not null,
    processed_at timestamptz not null
);
