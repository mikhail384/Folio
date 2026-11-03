CREATE TABLE IF NOT EXISTS folio_training_corpus (
  owner TEXT NOT NULL,
  name TEXT NOT NULL,
  id TEXT NOT NULL,
  object TEXT NOT NULL,
  PRIMARY KEY (owner, name, id)
);
