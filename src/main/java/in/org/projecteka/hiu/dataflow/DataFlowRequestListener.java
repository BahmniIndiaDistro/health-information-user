package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.DataFlowProperties;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyStructure;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_REQUEST_QUEUE;


@AllArgsConstructor
public class DataFlowRequestListener {
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final DataFlowClient dataFlowClient;
    private final DataFlowRepository dataFlowRepository;
    private final Decryptor decryptor;
    private final DataFlowProperties dataFlowProperties;
    private final CentralRegistry centralRegistry;


    @PostConstruct
    @SneakyThrows
    public void subscribe() {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(DATA_FLOW_REQUEST_QUEUE);
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            DataFlowRequest dataFlowRequest = convertToDataFlowRequest(message.getBody());
            logger.info("Received data flow request with consent id : " + dataFlowRequest.getConsent().getId());
            try {
                var dataRequestKeyMaterial = dataFlowRequestKeyMaterial();
                var keyMaterial = keyMaterial(dataRequestKeyMaterial);
                DataFlowRequest dataRequest = dataFlowRequest.toBuilder()
                        .keyMaterial(keyMaterial)
                        .build();

                logger.info("Initiating data flow request to consent manager");
                centralRegistry.token()
                        .flatMap(token -> dataFlowClient.initiateDataFlowRequest(dataRequest, token))
                        .flatMap(dataFlowRequestResponse ->
                                dataFlowRepository.addDataRequest(dataFlowRequestResponse.getTransactionId(),
                                        dataRequest)
                                        .then(dataFlowRepository.addKeys(
                                                dataFlowRequestResponse.getTransactionId(),
                                                dataRequestKeyMaterial)))
                        .block();
            } catch (Exception exception) {
                // TODO: Put the message in dead letter queue
                logger.fatal("Exception on key creation {exception}", exception);
            }
        };

        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    private DataFlowRequestKeyMaterial dataFlowRequestKeyMaterial() throws Exception {
        var keyPair = decryptor.generateKeyPair();
        var privateKey = decryptor.getBase64String(decryptor.getEncodedPrivateKey(keyPair.getPrivate()));
        var publicKey = decryptor.getBase64String(decryptor.getEncodedPublicKey(keyPair.getPublic()));
        return DataFlowRequestKeyMaterial.builder()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .randomKey(decryptor.generateRandomKey())
                .build();
    }

    private KeyMaterial keyMaterial(DataFlowRequestKeyMaterial dataFlowKeyMaterial) {
        logger.info("Creating KeyMaterials");
        return KeyMaterial.builder()
                .cryptoAlg(Decryptor.ALGORITHM)
                .curve(Decryptor.CURVE)
                .dhPublicKey(KeyStructure.builder()
                        .expiry(getExpiryDate())
                        .keyValue(dataFlowKeyMaterial.getPublicKey())
                        .parameters(Decryptor.EH_PUBLIC_KEY_PARAMETER)
                        .build())
                .nonce(dataFlowKeyMaterial.getRandomKey())
                .build();
    }

    private String getExpiryDate() {
        var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.DATE, dataFlowProperties.getOffsetInDays());
        return dateFormat.format(calendar.getTime());
    }

    @SneakyThrows
    private DataFlowRequest convertToDataFlowRequest(byte[] message) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(message, DataFlowRequest.class);
    }
}