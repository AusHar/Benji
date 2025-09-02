create table
    portfolio_position (
        id bigserial primary key,
        ticker varchar(12) not null,
        qty numeric(18, 6) not null default 0,
        basis numeric(18, 6) not null default 0
    );