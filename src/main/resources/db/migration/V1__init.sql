CREATE TABLE integration_jobs (
    id         UUID PRIMARY KEY,
    type       VARCHAR(24)  NOT NULL,
    channel    VARCHAR(20)  NOT NULL,
    reference  VARCHAR(80)  NOT NULL,
    payload    TEXT         NOT NULL,
    status     VARCHAR(10)  NOT NULL,
    attempts   INT          NOT NULL,
    last_error VARCHAR(500),
    result     VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT       NOT NULL,
    -- garante no banco que o mesmo pedido de um canal vira um único job, mesmo com duas leituras concorrentes
    CONSTRAINT uk_job_reference UNIQUE (type, channel, reference)
);

CREATE INDEX idx_jobs_status ON integration_jobs (status, updated_at);
CREATE INDEX idx_jobs_channel_status ON integration_jobs (channel, status);

CREATE TABLE sync_cursors (
    channel      VARCHAR(20) PRIMARY KEY,
    orders_since TIMESTAMP WITH TIME ZONE NOT NULL,
    last_run_at  TIMESTAMP WITH TIME ZONE,
    last_error   VARCHAR(500)
);
