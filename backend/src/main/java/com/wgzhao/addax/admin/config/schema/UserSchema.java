package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Upgrades the account tables: token versioning, profile columns and source grants. */
@Slf4j
public final class UserSchema
{
        public static final String ADD_USER_AUTH_VERSION_SQL =
        "alter table public.users add column if not exists auth_version integer not null default 0";

        public static final String ADD_USER_PROFILE_COLUMNS_SQL = """
        alter table public.users add column if not exists display_name varchar(100);
        alter table public.users add column if not exists email varchar(200);
        alter table public.users add column if not exists remark varchar(500);
        alter table public.users add column if not exists created_at timestamp without time zone
            not null default current_timestamp;
        alter table public.users add column if not exists last_login_at timestamp without time zone;
        alter table public.users add column if not exists password_changed_at timestamp without time zone;
        update public.users
           set display_name = username
         where display_name is null or btrim(display_name) = '';
        """;

        public static final String CREATE_USER_SOURCE_GRANT_SQL = """
        create table if not exists public.user_source_grant (
            username varchar(50) not null,
            source_id integer not null,
            access_level varchar(16) not null default 'OPERATE',
            granted_by varchar(50) not null,
            granted_at timestamp with time zone not null default current_timestamp,
            constraint user_source_grant_access_level_check
                check (access_level in ('VIEW', 'OPERATE')),
            constraint user_source_grant_pkey primary key (username, source_id)
        );
        create index if not exists idx_user_source_grant_source
            on public.user_source_grant (source_id);
        do $$
        begin
            if not exists (select 1 from pg_constraint where conname = 'user_source_grant_username_fkey') then
                alter table public.user_source_grant
                    add constraint user_source_grant_username_fkey
                    foreign key (username) references public.users(username) on delete cascade;
            end if;
            if to_regclass('public.etl_source') is not null
               and not exists (select 1 from pg_constraint where conname = 'user_source_grant_source_fkey') then
                alter table public.user_source_grant
                    add constraint user_source_grant_source_fkey
                    foreign key (source_id) references public.etl_source(id) on delete cascade;
            end if;
        end
        $$
        """;

    public static void upgrade(Connection connection, Statement statement) throws SQLException
    {
        // Authentication state is independent of the collection schema; upgrade it even on an
        // installation that has never created a source.
        if (!SchemaSupport.exists(connection, "tables", "users", null)) {
            return;
        }
        statement.execute(ADD_USER_AUTH_VERSION_SQL);
        statement.execute(ADD_USER_PROFILE_COLUMNS_SQL);
        statement.execute(CREATE_USER_SOURCE_GRANT_SQL);
    }

    private UserSchema()
    {
    }
}
