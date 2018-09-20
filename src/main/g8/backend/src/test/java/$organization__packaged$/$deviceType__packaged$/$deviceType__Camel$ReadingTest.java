package $organization;format="package"$.$deviceType;format="camel"$;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.cisco.streambed.HexString;
import com.cisco.streambed.durablequeue.DurableQueue;
import com.cisco.streambed.identity.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;
import scala.util.Right;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class $deviceType;format="Camel"$ReadingTest {

    private static ActorSystem system;
    private static Materializer mat;

    @Before
    public void setUp() {
        system = ActorSystem.create();
        mat = ActorMaterializer.create(system);
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void parseBytes() {
        Instant instant = Instant.now();
        int nwkAddr = 1;
        assertEquals(
                new $deviceType;format="Camel"$Reading(instant, nwkAddr, HexString.hexToBytes("025800a181f0")),
                new $deviceType;format="Camel"$Reading(instant, nwkAddr, BigDecimal.valueOf(200, 1), BigDecimal.valueOf(161, 1))
                );
    }

    @Test
    public void encodeDecodeJson() throws IOException {
        $deviceType;format="Camel"$Reading reading = new $deviceType;format="Camel"$Reading(
                Instant.EPOCH,
                1,
                BigDecimal.valueOf(200, 1),
                BigDecimal.valueOf(161, 1));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        assertEquals(
                mapper.valueToTree(reading),
                mapper.valueToTree(
                        mapper.readValue(
                                "{\"time\":\"1970-01-01T00:00:00Z\",\"nwkAddr\":1,\"temperature\":20.0,\"moisturePercentage\":16.1}",
                                $deviceType;format="Camel"$Reading.class)));
    }

    @Test
    public void appendAndTail() throws InterruptedException, ExecutionException, TimeoutException {
        Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret = secret ->
                CompletableFuture.completedFuture(Right.apply(
                        new Principal.SecretRetrieved(new Principal.AuthorizedSecret("2B7E151628AED2A6ABF7158809CF4F3C",
                                new FiniteDuration(10, TimeUnit.SECONDS)))));

        $deviceType;format="Camel"$Reading reading = new $deviceType;format="Camel"$Reading(Instant.EPOCH, 1, BigDecimal.valueOf(20), BigDecimal.valueOf(122, 1));

        Tuple2<$deviceType;format="Camel"$Reading, Long> result = Source
                .single(reading)
                .via($deviceType;format="Camel"$Reading.appender(getSecret, mat.executionContext()))
                .map(e -> {
                    DurableQueue.Send send = ((DurableQueue.Send) e.command());
                    long nwkAddr = send.key();
                    ByteString encryptedData = send.data();
                    return new DurableQueue.Received(
                            nwkAddr,
                            encryptedData,
                            0,
                            DurableQueue.EmptyHeaders(),
                            $deviceType;format="Camel"$Reading.DATA_UP_JSON_TOPIC);
                })
                .via($deviceType;format="Camel"$Reading.tailer(getSecret, mat.executionContext()))
                .runWith(Sink.head(), mat)
                .toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(reading, result._1());
    }
}
