CREATE TABLE IF NOT EXISTS transacciones (
    id BIGINT PRIMARY KEY,
    fecha DATE NOT NULL,
    monto DECIMAL(15, 2) NOT NULL,
    tipo VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS intereses (
    cuenta_id BIGINT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    saldo DECIMAL(15, 2) NOT NULL,
    edad INT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    tasa_interes DECIMAL(8, 4) DEFAULT 0,
    interes_calculado DECIMAL(15, 2) DEFAULT 0,
    saldo_final DECIMAL(15, 2) DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cuentas_anuales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    transaccion VARCHAR(50) NOT NULL,
    monto DECIMAL(15, 2) NOT NULL,
    descripcion VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    total_debitos DECIMAL(15, 2) NOT NULL,
    total_creditos DECIMAL(15, 2) NOT NULL,
    total_movimientos INT NOT NULL,
    cantidad_anomalias INT NOT NULL
);

CREATE TABLE IF NOT EXISTS informe_estados_cuenta_anuales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL,
    anio INT NOT NULL,
    total_depositos DECIMAL(15, 2) NOT NULL,
    total_retiros_compras DECIMAL(15, 2) NOT NULL,
    saldo_neto DECIMAL(15, 2) NOT NULL,
    cantidad_movimientos INT NOT NULL
);
