#!/bin/bash
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
CREATE DATABASE IF NOT EXISTS \`${AUTH_DB_NAME:-wearhouse_auth}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${USER_DB_NAME:-wearhouse_user}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${PRODUCT_DB_NAME:-wearhouse_product}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${ORDER_DB_NAME:-wearhouse_order}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${PAYMENT_DB_NAME:-wearhouse_payment}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${INVENTORY_DB_NAME:-wearhouse_inventory}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`${SETTLEMENT_DB_NAME:-wearhouse_settlement}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${MYSQL_USER:-wearhouse}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD:-wearhouse_password}';

GRANT ALL PRIVILEGES ON \`${AUTH_DB_NAME:-wearhouse_auth}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${USER_DB_NAME:-wearhouse_user}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${PRODUCT_DB_NAME:-wearhouse_product}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${ORDER_DB_NAME:-wearhouse_order}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${PAYMENT_DB_NAME:-wearhouse_payment}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${INVENTORY_DB_NAME:-wearhouse_inventory}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';
GRANT ALL PRIVILEGES ON \`${SETTLEMENT_DB_NAME:-wearhouse_settlement}\`.* TO '${MYSQL_USER:-wearhouse}'@'%';

FLUSH PRIVILEGES;
EOSQL
