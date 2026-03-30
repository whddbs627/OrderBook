create table if not exists ledger_entries (
    ledger_entry_id bigserial primary key,
    trade_id varchar(64) not null,
    account_id varchar(64) not null,
    symbol varchar(32) not null,
    entry_type varchar(32) not null,
    amount numeric(19, 4) not null,
    quantity bigint not null,
    reference_id varchar(64) not null,
    occurred_at timestamptz not null,
    trace_id varchar(64) not null
);

create index if not exists idx_ledger_entries_trade_id on ledger_entries (trade_id);
create index if not exists idx_ledger_entries_account_id on ledger_entries (account_id, occurred_at);

create table if not exists balances (
    account_id varchar(64) primary key,
    cash_balance numeric(19, 4) not null,
    updated_at timestamptz not null
);

create table if not exists positions (
    account_id varchar(64) not null,
    symbol varchar(32) not null,
    quantity bigint not null,
    updated_at timestamptz not null,
    primary key (account_id, symbol)
);

insert into balances (account_id, cash_balance, updated_at)
values
    ('ACC-001', 1000000.0000, now()),
    ('ACC-002', 1000000.0000, now())
on conflict (account_id) do nothing;

insert into positions (account_id, symbol, quantity, updated_at)
values
    ('ACC-001', 'AAPL', 300, now()),
    ('ACC-001', 'MSFT', 200, now()),
    ('ACC-002', 'AAPL', 150, now()),
    ('ACC-002', 'TSLA', 50, now())
on conflict (account_id, symbol) do nothing;
