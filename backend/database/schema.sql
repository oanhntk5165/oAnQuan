CREATE DATABASE IF NOT EXISTS oanquan
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE oanquan;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    points        INT          NOT NULL DEFAULT 0,
    rating        INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_points (points DESC, rating DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS matches (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    player1_id       BIGINT NOT NULL,
    player2_id       BIGINT NULL,
    mode             ENUM('ROOM', 'RANDOM', 'BOT', 'FRIENDS', 'RANKED') NOT NULL,
    time_control_ms  INT NOT NULL,
    score1           INT NULL,
    score2           INT NULL,
    time_used1_ms    INT NULL,
    time_used2_ms    INT NULL,
    rating_change1   INT NOT NULL DEFAULT 0,
    rating_change2   INT NOT NULL DEFAULT 0,
    points_change1   INT NOT NULL DEFAULT 0,
    points_change2   INT NOT NULL DEFAULT 0,
    result           ENUM('P1_WIN', 'P2_WIN', 'DRAW') NULL,
    end_reason       ENUM('NORMAL', 'TIMEOUT', 'RESIGN', 'DISCONNECT', 'PASS_LIMIT') NULL,
    started_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at         TIMESTAMP NULL,
    CONSTRAINT fk_matches_p1 FOREIGN KEY (player1_id) REFERENCES users(id),
    CONSTRAINT fk_matches_p2 FOREIGN KEY (player2_id) REFERENCES users(id),
    INDEX idx_matches_p1 (player1_id, ended_at),
    INDEX idx_matches_p2 (player2_id, ended_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS match_moves (
    match_id       BIGINT NOT NULL,
    move_no        INT NOT NULL,
    player_slot    TINYINT NOT NULL,
    action         ENUM('MOVE', 'PASS', 'AUTO_MOVE', 'TIMEOUT') NOT NULL,
    start_pit      TINYINT NULL,
    direction      TINYINT NULL,
    gained         INT NOT NULL,
    time_spent_ms  INT NOT NULL,
    board_after    JSON NOT NULL,
    PRIMARY KEY (match_id, move_no),
    CONSTRAINT fk_moves_match FOREIGN KEY (match_id) REFERENCES matches(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS chat_messages (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id  BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content   VARCHAR(500) NOT NULL,
    sent_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_chat_match (match_id, sent_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS schema_metadata (
    schema_key   VARCHAR(64) PRIMARY KEY,
    schema_value VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

DROP PROCEDURE IF EXISTS upgrade_oanquan_schema;
DELIMITER //
CREATE PROCEDURE upgrade_oanquan_schema()
BEGIN
    DECLARE v_done BOOLEAN DEFAULT FALSE;
    DECLARE v_migrated INT DEFAULT 0;
    DECLARE v_match_id BIGINT;
    DECLARE v_player1_id BIGINT;
    DECLARE v_player2_id BIGINT;
    DECLARE v_result VARCHAR(16);
    DECLARE v_points1 INT;
    DECLARE v_points2 INT;
    DECLARE v_delta1 INT;
    DECLARE v_delta2 INT;
    DECLARE v_next1 INT;
    DECLARE v_next2 INT;
    DECLARE v_tier1 INT;
    DECLARE v_tier2 INT;
    DECLARE ranked_matches CURSOR FOR
        SELECT id, player1_id, player2_id, result
        FROM matches
        WHERE mode = 'RANKED' AND ended_at IS NOT NULL AND result IS NOT NULL
        ORDER BY started_at, id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'rating'
    ) THEN
        ALTER TABLE users ADD COLUMN rating INT NOT NULL DEFAULT 0 AFTER points;
    END IF;
    ALTER TABLE users MODIFY rating INT NOT NULL DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_points'
    ) THEN
        ALTER TABLE users ADD INDEX idx_users_points (points DESC, rating DESC);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'player2_id'
    ) THEN
        ALTER TABLE matches ADD COLUMN player2_id BIGINT NULL AFTER player1_id;
    END IF;
    ALTER TABLE matches MODIFY player2_id BIGINT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'mode'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN mode ENUM('ROOM', 'RANDOM', 'BOT', 'FRIENDS', 'RANKED') NOT NULL DEFAULT 'RANKED';
    ELSE
        ALTER TABLE matches
            MODIFY mode ENUM('ROOM', 'RANDOM', 'BOT', 'FRIENDS', 'RANKED') NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'rating_change1'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN rating_change1 INT NOT NULL DEFAULT 0 AFTER time_used2_ms;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'rating_change2'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN rating_change2 INT NOT NULL DEFAULT 0 AFTER rating_change1;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'points_change1'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN points_change1 INT NOT NULL DEFAULT 0 AFTER rating_change2;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'points_change2'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN points_change2 INT NOT NULL DEFAULT 0 AFTER points_change1;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'matches' AND column_name = 'end_reason'
    ) THEN
        ALTER TABLE matches
            ADD COLUMN end_reason ENUM('NORMAL', 'TIMEOUT', 'RESIGN', 'DISCONNECT', 'PASS_LIMIT') NULL;
    ELSE
        ALTER TABLE matches
            MODIFY end_reason ENUM('NORMAL', 'TIMEOUT', 'RESIGN', 'DISCONNECT', 'PASS_LIMIT') NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'match_moves' AND column_name = 'action'
    ) THEN
        ALTER TABLE match_moves
            ADD COLUMN action ENUM('MOVE', 'PASS', 'AUTO_MOVE', 'TIMEOUT') NOT NULL DEFAULT 'MOVE'
            AFTER player_slot;
    ELSE
        ALTER TABLE match_moves
            MODIFY action ENUM('MOVE', 'PASS', 'AUTO_MOVE', 'TIMEOUT') NOT NULL;
    END IF;
    ALTER TABLE match_moves
        MODIFY start_pit TINYINT NULL,
        MODIFY direction TINYINT NULL;

    SELECT COUNT(*) INTO v_migrated
    FROM schema_metadata
    WHERE schema_key = 'rank_points_v1';

    IF v_migrated = 0 THEN
        START TRANSACTION;
        UPDATE users SET points = 0, rating = rating - 1000;
        DROP TEMPORARY TABLE IF EXISTS rank_points_recalc;
        CREATE TEMPORARY TABLE rank_points_recalc (
            match_id BIGINT PRIMARY KEY,
            points_change1 INT NOT NULL,
            points_change2 INT NOT NULL
        ) ENGINE=InnoDB;

        SET v_done = FALSE;
        OPEN ranked_matches;
        recalc_loop: LOOP
            FETCH ranked_matches INTO v_match_id, v_player1_id, v_player2_id, v_result;
            IF v_done THEN
                LEAVE recalc_loop;
            END IF;

            SELECT points INTO v_points1 FROM users WHERE id = v_player1_id;
            SET v_tier1 = LEAST(v_points1 DIV 100, 5);
            CASE v_tier1
                WHEN 0 THEN SET v_delta1 = IF(v_result = 'P1_WIN', 4, IF(v_result = 'DRAW', 1, -1));
                WHEN 1 THEN SET v_delta1 = IF(v_result = 'P1_WIN', 4, IF(v_result = 'DRAW', 1, -2));
                WHEN 2 THEN SET v_delta1 = IF(v_result = 'P1_WIN', 3, IF(v_result = 'DRAW', 0, -2));
                WHEN 3 THEN SET v_delta1 = IF(v_result = 'P1_WIN', 3, IF(v_result = 'DRAW', 0, -3));
                WHEN 4 THEN SET v_delta1 = IF(v_result = 'P1_WIN', 2, IF(v_result = 'DRAW', 0, -3));
                ELSE SET v_delta1 = IF(v_result = 'P1_WIN', 2, IF(v_result = 'DRAW', -1, -4));
            END CASE;
            SET v_next1 = GREATEST(0, v_points1 + v_delta1);
            SET v_delta1 = v_next1 - v_points1;
            UPDATE users SET points = v_next1 WHERE id = v_player1_id;

            SET v_delta2 = 0;
            IF v_player2_id IS NOT NULL THEN
                SELECT points INTO v_points2 FROM users WHERE id = v_player2_id;
                SET v_tier2 = LEAST(v_points2 DIV 100, 5);
                CASE v_tier2
                    WHEN 0 THEN SET v_delta2 = IF(v_result = 'P2_WIN', 4, IF(v_result = 'DRAW', 1, -1));
                    WHEN 1 THEN SET v_delta2 = IF(v_result = 'P2_WIN', 4, IF(v_result = 'DRAW', 1, -2));
                    WHEN 2 THEN SET v_delta2 = IF(v_result = 'P2_WIN', 3, IF(v_result = 'DRAW', 0, -2));
                    WHEN 3 THEN SET v_delta2 = IF(v_result = 'P2_WIN', 3, IF(v_result = 'DRAW', 0, -3));
                    WHEN 4 THEN SET v_delta2 = IF(v_result = 'P2_WIN', 2, IF(v_result = 'DRAW', 0, -3));
                    ELSE SET v_delta2 = IF(v_result = 'P2_WIN', 2, IF(v_result = 'DRAW', -1, -4));
                END CASE;
                SET v_next2 = GREATEST(0, v_points2 + v_delta2);
                SET v_delta2 = v_next2 - v_points2;
                UPDATE users SET points = v_next2 WHERE id = v_player2_id;
            END IF;

            INSERT INTO rank_points_recalc (match_id, points_change1, points_change2)
            VALUES (v_match_id, v_delta1, v_delta2);
        END LOOP;
        CLOSE ranked_matches;

        UPDATE matches m
        JOIN rank_points_recalc r ON r.match_id = m.id
        SET m.points_change1 = r.points_change1,
            m.points_change2 = r.points_change2;
        DROP TEMPORARY TABLE rank_points_recalc;

        INSERT INTO schema_metadata (schema_key, schema_value)
        VALUES ('rank_points_v1', 'recalculated from completed RANKED matches');
        COMMIT;
    END IF;
END//
DELIMITER ;

CALL upgrade_oanquan_schema();
DROP PROCEDURE upgrade_oanquan_schema;
