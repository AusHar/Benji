create table journal_entries (
    id          bigserial primary key,
    body        text not null,
    entry_date  date not null default current_date,
    created_at  timestamp with time zone not null default now(),
    updated_at  timestamp with time zone not null default now(),
    constraint uq_journal_entries_entry_date unique (entry_date)
);

create table journal_entry_tickers (
    entry_id    bigint not null references journal_entries(id) on delete cascade,
    ticker      text not null,
    primary key (entry_id, ticker)
);

create table journal_entry_tags (
    entry_id    bigint not null references journal_entries(id) on delete cascade,
    tag         text not null,
    primary key (entry_id, tag)
);

create table journal_goals (
    id               bigserial primary key,
    label            text not null,
    goal_type        text not null check (goal_type in ('milestone', 'habit')),
    target_value     numeric,
    milestone_value  numeric,
    deadline         date,
    created_at       timestamp with time zone not null default now()
);
