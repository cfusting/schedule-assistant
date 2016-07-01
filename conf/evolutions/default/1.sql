# --- !Ups

create table users (
  id text NOT NULL PRIMARY KEY,
  action text NOT NULL,
  scheduled timestamptz,
  event_id text
);

# --- !Downs

drop table if exists users ;
