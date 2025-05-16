package de.ait.restaurantapp.exception;

// unchecked‑исключение
public class NoAvailableTableException extends RuntimeException {
    public NoAvailableTableException() {
        super();
    }

    public NoAvailableTableException(String message) {
        super(message);
    }
}

