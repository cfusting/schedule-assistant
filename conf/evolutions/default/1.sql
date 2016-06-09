# --- !Ups

create table USERS (
ID VARCHAR(128) NOT NULL PRIMARY KEY,
ACTION VARCHAR(128) NOT NULL
);

# --- !Downs

drop table if exists USERS;