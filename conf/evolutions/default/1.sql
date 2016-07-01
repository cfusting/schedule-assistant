# --- !Ups

create table users (
  id text NOT NULL PRIMARY KEY,
  action text NOT NULL,
  scheduled timestamptz,
  event_id text,
  first_name text,
  last_name text
);

# --- !Downs

drop table if exists users ;
