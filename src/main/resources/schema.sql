create table if not exists connection_config
(
    id      integer not null
        constraint connection_config_pk
            primary key autoincrement,
    name    text    not null,
    config  text    not null,
    running integer default 0 not null
);

