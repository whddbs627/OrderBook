alter table order_query_view drop constraint if exists order_query_view_client_order_id_key;

create unique index if not exists uq_order_query_view_account_client_order_id
    on order_query_view (account_id, client_order_id);
