alter table mounts
    add column if not exists capacity_bytes bigint null;

