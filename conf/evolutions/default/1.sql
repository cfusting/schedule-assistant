# --- !Ups

create table users (
  id text NOT NULL PRIMARY KEY,
  action text NOT NULL,
  scheduled timestamptz
);

create table availability (
  id serial PRIMARY KEY,
  user_id text NOT NULL,
  period tstzrange NOT NULL
);

create table messages (
  id text PRIMARY KEY,
  seq int NOT NULL
);

# --- !Downs

drop table if exists users ;
drop table if exists availability;