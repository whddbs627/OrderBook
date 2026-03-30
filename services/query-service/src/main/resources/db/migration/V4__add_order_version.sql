alter table order_query_view
    add column if not exists version bigint not null default 0;
