alter table mounts
    add column if not exists shared_enabled boolean not null default false;

