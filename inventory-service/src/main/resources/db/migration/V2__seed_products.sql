INSERT INTO products (
    id,
    sku,
    name,
    available_quantity,
    reserved_quantity
)
VALUES
    (101, 'LAPTOP-001', 'Laptop', 10, 0),
    (205, 'MOUSE-001', 'Wireless Mouse', 50, 0),
    (310, 'KEYBOARD-001', 'Mechanical Keyboard', 25, 0),
    (420, 'MONITOR-001', '27-inch Monitor', 15, 0);

SELECT setval(
               pg_get_serial_sequence('products', 'id'),
               (SELECT MAX(id) FROM products)
       );
