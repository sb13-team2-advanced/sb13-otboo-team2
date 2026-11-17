package codeit.ootd.domain.auth.service;

public interface TemporaryPasswordMailSender {

    void send(String email, String temporaryPassword);

}
