package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.exception.HpinNotFoundException;
import in.org.projecteka.hiu.consent.model.consentmanager.Identifier;
import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static in.org.projecteka.hiu.user.TestBuilders.string;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    private final String username = string();
    private final String validHpin = string();

    @BeforeEach
    void setUp() {
        initMocks(this);
        userService = new UserService(userRepository);
    }

    @Test
    void shouldCreateRequesterWhenValidHPINExists() {
        when(userRepository.getHpinForUser(username))
                .thenReturn(Mono.just(validHpin));

        Requester expectedRequester = Requester.builder()
                .name(username)
                .identifier(Identifier.builder()
                        .type(Constants.REQUESTER_IDENTIFIER_TYPE)
                        .value(validHpin)
                        .system(Constants.REQUESTER_IDENTIFIER_SYSTEM)
                        .build())
                .build();

        StepVerifier.create(userService.toRequester(username))
                .expectNext(expectedRequester)
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenHPINIsNull() {
        when(userRepository.getHpinForUser(username))
                .thenReturn(Mono.empty());

        StepVerifier.create(userService.toRequester(username))
                .expectErrorMatches(error ->
                    error instanceof HpinNotFoundException &&
                    error.getMessage().equals(Constants.HPIN_NOT_FOUND_ERROR + username))
                .verify();
    }

    @Test
    void shouldReturnErrorWhenHPINIsEmpty() {
        when(userRepository.getHpinForUser(username))
                .thenReturn(Mono.just("  "));

        StepVerifier.create(userService.toRequester(username))
                .expectErrorMatches(error ->
                    error instanceof HpinNotFoundException &&
                    error.getMessage().equals(Constants.HPIN_NOT_FOUND_ERROR + username))
                .verify();
    }

    @Test
    void shouldPropagateRepositoryErrors() {
        RuntimeException expectedError = new RuntimeException("Database error");
        when(userRepository.getHpinForUser(username))
                .thenReturn(Mono.error(expectedError));

        StepVerifier.create(userService.toRequester(username))
                .expectErrorMatches(error -> error == expectedError)
                .verify();
    }
}
