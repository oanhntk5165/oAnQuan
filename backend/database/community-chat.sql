-- Additive migration for existing installations (also created lazily by the DAO).
CREATE TABLE IF NOT EXISTS community_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    content VARCHAR(500) NOT NULL,
    sent_at_ms BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
