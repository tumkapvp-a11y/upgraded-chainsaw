package me.x1no4ekk.core.database;

import java.util.List;

/**
 * DDL for every Core table. All statements are idempotent.
 */
final class Schema {

    private Schema() {
    }

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_users (
                uuid            CHAR(36) CHARACTER SET ascii NOT NULL,
                name            VARCHAR(16)  NOT NULL,
                nickname        VARCHAR(128) NULL,
                fly             TINYINT(1)   NOT NULL DEFAULT 0,
                god             TINYINT(1)   NOT NULL DEFAULT 0,
                vanish          TINYINT(1)   NOT NULL DEFAULT 0,
                tp_toggle       TINYINT(1)   NOT NULL DEFAULT 1,
                msg_toggle      TINYINT(1)   NOT NULL DEFAULT 1,
                chat_toggle     TINYINT(1)   NOT NULL DEFAULT 1,
                social_spy      TINYINT(1)   NOT NULL DEFAULT 0,
                bossbar_toggle  TINYINT(1)   NOT NULL DEFAULT 1,
                last_location   VARCHAR(255) NULL,
                first_join      BIGINT       NOT NULL,
                last_join       BIGINT       NOT NULL,
                PRIMARY KEY (uuid),
                INDEX idx_core_users_name (name)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_homes (
                uuid        CHAR(36) CHARACTER SET ascii NOT NULL,
                name        VARCHAR(32) NOT NULL,
                world       VARCHAR(64) NOT NULL,
                x           DOUBLE      NOT NULL,
                y           DOUBLE      NOT NULL,
                z           DOUBLE      NOT NULL,
                yaw         FLOAT       NOT NULL,
                pitch       FLOAT       NOT NULL,
                created_at  BIGINT      NOT NULL,
                PRIMARY KEY (uuid, name)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_warps (
                name         VARCHAR(32)  NOT NULL,
                owner_uuid   CHAR(36) CHARACTER SET ascii NULL,
                system_warp  TINYINT(1)   NOT NULL DEFAULT 0,
                world        VARCHAR(64)  NOT NULL,
                x            DOUBLE       NOT NULL,
                y            DOUBLE       NOT NULL,
                z            DOUBLE       NOT NULL,
                yaw          FLOAT        NOT NULL,
                pitch        FLOAT        NOT NULL,
                icon         VARCHAR(64)  NULL,
                description  VARCHAR(255) NULL,
                visits       INT          NOT NULL DEFAULT 0,
                created_at   BIGINT       NOT NULL,
                PRIMARY KEY (name),
                INDEX idx_core_warps_owner (owner_uuid)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_enderchests (
                uuid      CHAR(36) CHARACTER SET ascii NOT NULL,
                page      INT        NOT NULL,
                contents  MEDIUMBLOB NOT NULL,
                PRIMARY KEY (uuid, page)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_stats (
                uuid       CHAR(36) CHARACTER SET ascii NOT NULL,
                kills      INT    NOT NULL DEFAULT 0,
                deaths     INT    NOT NULL DEFAULT 0,
                mob_kills  INT    NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid),
                INDEX idx_core_stats_kills (kills),
                INDEX idx_core_stats_deaths (deaths)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_kit_cooldowns (
                uuid       CHAR(36) CHARACTER SET ascii NOT NULL,
                kit        VARCHAR(64) NOT NULL,
                last_used  BIGINT      NOT NULL,
                PRIMARY KEY (uuid, kit)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_cooldowns (
                uuid          CHAR(36) CHARACTER SET ascii NOT NULL,
                cooldown_key  VARCHAR(64) NOT NULL,
                expires_at    BIGINT      NOT NULL,
                PRIMARY KEY (uuid, cooldown_key),
                INDEX idx_core_cooldowns_expires (expires_at)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS core_ignores (
                uuid          CHAR(36) CHARACTER SET ascii NOT NULL,
                ignored_uuid  CHAR(36) CHARACTER SET ascii NOT NULL,
                PRIMARY KEY (uuid, ignored_uuid)
            )""" + TABLE_OPTIONS
    );
}
