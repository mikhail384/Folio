import {sqliteTable,text,integer,index,primaryKey} from 'drizzle-orm/sqlite-core';
export const sessions=sqliteTable('folio_sessions',{owner:text('owner').primaryKey(),channel:text('channel').notNull().unique(),secret:text('secret').notNull(),seen:integer('seen').notNull().default(0)});
export const tasks=sqliteTable('folio_tasks',{id:text('id').primaryKey(),channel:text('channel').notNull(),state:integer('state').notNull().default(0),lease:text('lease'),created:integer('created').notNull(),expires:integer('expires').notNull()},t=>[index('folio_tasks_channel_idx').on(t.channel,t.state,t.created)]);
export const profiles=sqliteTable('folio_profiles',{owner:text('owner').notNull(),name:text('name').notNull(),object:text('object').notNull()},t=>[primaryKey({columns:[t.owner,t.name]})]);
