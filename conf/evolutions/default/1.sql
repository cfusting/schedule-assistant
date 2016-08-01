# --- !Ups

create table bot_users (
  id text NOT NULL PRIMARY KEY,
  action text NOT NULL,
  scheduled timestamptz,
  eventid text,
  firstname text,
  lastname text
);


create table "user" (
  userid text NOT NULL PRIMARY KEY,
  firstname text,
  lastname text,
  fullname text,
  email text,
  avatarurl text
);

create table logininfo (
  id serial NOT NULL PRIMARY KEY,
  providerid text NOT NULL,
  providerkey text NOT NULL
);

create table userlogininfo (
  userid text NOT NULL,
  logininfoid bigint NOT NULL
);


create table passwordinfo (
  hasher text NOT NULL,
  password text NOT NULL,
  salt text,
  logininfoid bigint NOT NULL
);

create table oauth1info (
  id serial NOT NULL PRIMARY KEY,
  token text NOT NULL,
  secret text NOT NULL,
  logininfoid bigint NOT NULL
);

create table oauth2info (
  id serial NOT NULL PRIMARY KEY,
  accesstoken text NOT NULL,
  tokentype text,
  expiresin int,
  refreshtoken text,
  logininfoid bigint NOT NULL
);

create table openidinfo (
  id text NOT NULL PRIMARY KEY,
  logininfoid bigint NOT NULL
);


create table openidattributes (
  id text NOT NULL,
  "key" text NOT NULL,
  value text NOT NULL
);

create table googletofacebookpage (
  googlelogininfoid bigint NOT NULL,
  facebookpageid bigint NOT NULL,
  accesstoken text NOT NULL,
  active boolean NOT NULL,
  calendarid text NOT NULL,
  name text NOT NULL,
  eventnoun text NOT NULL,
  CONSTRAINT googlelogininfoid_facebookpageid_pk PRIMARY KEY (googlelogininfoid, facebookpageid)
);

# --- !Downs

drop table if exists bot_users;
drop table if exists "user";
drop table if exists logininfo;
drop table if exists passwordinfo;
drop table if exists oauth1info;
drop table if exists oauth2info;
drop table if exists userlogininfo;
drop table if exists openidinfo;
drop table if exists openidattributes;
drop table if exists googletofacebookpage;
