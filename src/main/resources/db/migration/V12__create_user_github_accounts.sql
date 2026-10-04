-- Purpose: GitHub account a user connects through OAuth; public data copied for the profile (FR-USER-27, BR-USER-22).
--          No GitHub token is stored.
-- Author: MinhTien | Created: 2026-10-04
CREATE TABLE user_github_accounts (
        user_id           uuid         NOT NULL
    ,   github_id         bigint       NOT NULL
    ,   login             varchar(39)  NOT NULL
    ,   name              varchar(255)
    ,   avatar_url        varchar(500) NOT NULL
    ,   html_url          varchar(500) NOT NULL
    ,   bio               varchar(500)
    ,   location          varchar(255)
    ,   public_repos      integer      NOT NULL DEFAULT 0
    ,   followers         integer      NOT NULL DEFAULT 0
    ,   following         integer      NOT NULL DEFAULT 0
    ,   github_created_at timestamptz  NOT NULL
    ,   connected_at      timestamptz  NOT NULL DEFAULT now()
    ,   synced_at         timestamptz  NOT NULL DEFAULT now(),

    -- Constraint --
    CONSTRAINT pk_user_github_accounts
        PRIMARY KEY (user_id),
    CONSTRAINT uq_user_github_accounts_github_id
        UNIQUE (github_id),
    CONSTRAINT ck_user_github_accounts_github_id
        CHECK (github_id > 0),
    CONSTRAINT ck_user_github_accounts_counts
        CHECK (public_repos >= 0 AND followers >= 0 AND following >= 0),
    CONSTRAINT fk_user_github_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);
