CREATE TABLE IF NOT EXISTS transacciones_procesadas (
    id INTEGER PRIMARY KEY,
    fecha DATE NOT NULL,
    monto NUMERIC(14, 2) NOT NULL,
    tipo VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS intereses_mensuales (
    cuenta_id INTEGER PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    saldo_inicial NUMERIC(14, 2) NOT NULL,
    tasa NUMERIC(8, 4) NOT NULL,
    saldo_final NUMERIC(14, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS movimientos_anuales (
    movimiento_id SERIAL PRIMARY KEY,
    cuenta_id INTEGER NOT NULL,
    fecha DATE NOT NULL,
    transaccion VARCHAR(30) NOT NULL,
    monto NUMERIC(14, 2) NOT NULL,
    descripcion VARCHAR(150) NOT NULL,
    UNIQUE (cuenta_id, fecha, transaccion, monto, descripcion)
);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    resumen_id SERIAL PRIMARY KEY,
    job_execution_id BIGINT NOT NULL,
    registros_leidos INTEGER NOT NULL,
    registros_procesados INTEGER NOT NULL,
    registros_escritos INTEGER NOT NULL,
    registros_omitidos INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL
);
