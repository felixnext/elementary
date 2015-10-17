
CREATE SCHEMA ElementaryQuestions;

USE ElementaryQuestions;

DROP TABLE if exists Answers;
DROP TABLE if exists Questions;
DROP TABLE if exists Corpora;

CREATE TABLE if not exists Corpora (
  name varchar(50) not null primary key,
  description text,
  deprecated bit not null default b'0'
);

CREATE TABLE if not exists Questions (
  id int not null auto_increment,
  corpus varchar(50) not null,
  question text not null,
  qtype varchar(50),
  atype varchar(50),
  deprecated bit not null default b'0',
  Primary Key (id, corpus),
  Foreign Key (corpus) References Corpora(name) on update cascade on delete cascade
);

CREATE TABLE if not exists Answers (
  id int not null auto_increment,
  question int not null,
  corpus varchar(50) not null,
  answer text not null,
  Primary Key(id, question, corpus),
  Foreign Key (question, corpus) References Questions(id, corpus) on update cascade on delete cascade
);
