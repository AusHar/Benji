alter table portfolio_position
    add constraint uq_portfolio_position_ticker unique (ticker);

create table finance_transaction (
    id varchar(36) primary key,
    posted_at timestamp with time zone not null,
    description varchar(255) not null,
    amount numeric(18, 2) not null,
    category varchar(64),
    notes text
);

create index finance_transaction_posted_at_idx
    on finance_transaction (posted_at desc);

create index finance_transaction_category_idx
    on finance_transaction (category);
