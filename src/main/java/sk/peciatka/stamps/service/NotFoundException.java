package sk.peciatka.stamps.service;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + "_not_found");
    }
}
