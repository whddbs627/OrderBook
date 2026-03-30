alter table orders drop constraint if exists orders_client_order_id_key;

create unique index if not exists uq_orders_account_client_order_id
    on orders (account_id, client_order_id);
