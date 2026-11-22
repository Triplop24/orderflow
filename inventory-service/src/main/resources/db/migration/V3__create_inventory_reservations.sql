CREATE TABLE inventory_reservations (
                                        order_id BIGINT PRIMARY KEY,

                                        status VARCHAR(20) NOT NULL
                                            CHECK (status IN ('PROCESSING', 'RESERVED', 'REJECTED')),

                                        reason VARCHAR(500),

                                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
