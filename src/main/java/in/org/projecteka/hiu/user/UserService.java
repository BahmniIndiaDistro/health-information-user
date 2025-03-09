package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.exception.HpinNotFoundException;
import in.org.projecteka.hiu.consent.model.consentmanager.Identifier;

import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Mono<Requester> toRequester(String username){
        return getIdentifierForUser(username).map(identifier -> Requester.builder()
                .name(username)
                .identifier(identifier)
                .build());
    }

    private Mono<Identifier> getIdentifierForUser(String username){
        return userRepository.getHpinForUser(username)
                .switchIfEmpty(Mono.error(new HpinNotFoundException(
                        Constants.HPIN_NOT_FOUND_ERROR + username)))
                .flatMap(identifierValue -> {
                    if (identifierValue.trim().isEmpty()) {
                        return Mono.error(new HpinNotFoundException(Constants.HPIN_NOT_FOUND_ERROR + username));
                    }
                    return Mono.just(Identifier.builder()
                            .type(Constants.REQUESTER_IDENTIFIER_TYPE)
                            .value(identifierValue)
                            .system(Constants.REQUESTER_IDENTIFIER_SYSTEM)
                            .build());
                });
    }
}

