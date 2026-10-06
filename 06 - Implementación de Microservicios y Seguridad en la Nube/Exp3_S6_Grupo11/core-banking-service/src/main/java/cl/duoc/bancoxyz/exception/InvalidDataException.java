package cl.duoc.bancoxyz.exception;

// Se utiliza cuando un registro no puede ser procesado correctamente
public class InvalidDataException extends RuntimeException {

    public InvalidDataException(String message) {
        super(message);
    }
}
