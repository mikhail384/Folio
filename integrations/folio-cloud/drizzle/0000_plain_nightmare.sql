CREATE TABLE `folio_profiles` (
	`owner` text NOT NULL,
	`name` text NOT NULL,
	`object` text NOT NULL,
	PRIMARY KEY(`owner`, `name`)
);
--> statement-breakpoint
CREATE TABLE `folio_sessions` (
	`owner` text PRIMARY KEY NOT NULL,
	`channel` text NOT NULL,
	`secret` text NOT NULL,
	`seen` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `folio_sessions_channel_unique` ON `folio_sessions` (`channel`);--> statement-breakpoint
CREATE TABLE `folio_tasks` (
	`id` text PRIMARY KEY NOT NULL,
	`channel` text NOT NULL,
	`state` integer DEFAULT 0 NOT NULL,
	`lease` text,
	`created` integer NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `folio_tasks_channel_idx` ON `folio_tasks` (`channel`,`state`,`created`);