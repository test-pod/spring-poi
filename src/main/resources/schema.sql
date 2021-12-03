create table if not exists blog
(
    id      integer not null
        constraint blog_pk
            primary key autoincrement,
    title   TEXT default '' not null,
    content text default ''
);
