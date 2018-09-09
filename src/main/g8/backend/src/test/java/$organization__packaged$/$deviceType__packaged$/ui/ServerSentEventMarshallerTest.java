package $organization;format="package"$.$deviceType;format="camel"$.ui;

import akka.http.javadsl.model.sse.ServerSentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import $organization;format="package"$.$deviceType;format="camel"$.$deviceType;format="Camel"$Reading;
import com.github.huntc.lora.controlplane.EndDeviceEvents;
import com.github.huntc.lora.packet.FCnt;
import org.junit.Test;
import scala.Option;
import spray.json.JsonParser;
import spray.json.ParserInput\$;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.*;

public class ServerSentEventMarshallerTest {

    @Test
    public void endDeviceEvents() {
        int nwkAddr = 1;

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.NwkAddrUpdated(nwkAddr, 2), 0L),
                "{\"nwkAddr\":1,\"devEUI\":2,\"type\":\"NwkAddrUpdated\"}",
                "NwkAddrUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.NwkAddrRemoved(nwkAddr), 0L),
                "{\"nwkAddr\":1,\"type\":\"NwkAddrRemoved\"}",
                "NwkAddrRemoved",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.SecretsUpdated(nwkAddr), 0L),
                "{\"nwkAddr\":1,\"type\":\"SecretsUpdated\"}",
                "SecretsUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(
                        new EndDeviceEvents.CountersUpdated(
                                nwkAddr,
                                Option.apply(new FCnt(2)),
                                Option.apply(new FCnt(3)),
                                Option.apply(new FCnt(4))),
                        0L),
                "{\"nwkAddr\":1,\"fCntUp\":3,\"afCntDown\":2,\"nfCntDown\":4,\"type\":\"CountersUpdated\"}",
                "CountersUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(
                        new EndDeviceEvents.PositionUpdated(
                                nwkAddr,
                                Instant.EPOCH,
                                new EndDeviceEvents.LatLng(
                                        scala.math.BigDecimal.valueOf(1),
                                        scala.math.BigDecimal.valueOf(2),
                                        Option.apply(scala.math.BigDecimal.valueOf(3)))),
                        0L),
                "{\"nwkAddr\":1,\"time\":\"1970-01-01T00:00:00Z\",\"position\":{\"lat\":1,\"lng\":2,\"alt\":3},\"type\":\"PositionUpdated\"}",
                "PositionUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.SessionKeysUpdated(nwkAddr), 0L),
                "{\"nwkAddr\":1,\"type\":\"SessionKeysUpdated\"}",
                "SessionKeysUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.TopicUpdated(nwkAddr, "some-topic"), 0L),
                "{\"nwkAddr\":1,\"topic\":\"some-topic\",\"type\":\"TopicUpdated\"}",
                "TopicUpdated",
                "0"
        );

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(new EndDeviceEvents.VersionUpdated(nwkAddr, 1), 0L),
                "{\"nwkAddr\":1,\"version\":1,\"type\":\"VersionUpdated\"}",
                "VersionUpdated",
                "0"
        );
    }

    @Test
    public void $deviceType;format="camel"$Events() throws JsonProcessingException {

        assertEqualsSse(
                ServerSentEventMarshaller.toServerSentEvent(
                        new $deviceType;format="Camel"$Reading(
                                Instant.EPOCH,
                                1,
                                BigDecimal.valueOf(1),
                                BigDecimal.valueOf(2)),
                        1L),
                "{\"nwkAddr\":1,\"time\":\"1970-01-01T00:00:00Z\",\"temperature\":1,\"moisturePercentage\":2}",
                "$deviceType;format="Camel"$Reading",
                "1"
        );
    }

    private void assertEqualsSse(ServerSentEvent sse, String data, String type, String id) {
        assertEquals(
                new JsonParser(ParserInput\$.MODULE\$.apply(data)).parseJsValue(),
                new JsonParser(ParserInput\$.MODULE\$.apply(sse.getData())).parseJsValue());
        assertEquals(Optional.ofNullable(type), sse.getEventType());
        assertEquals(Optional.ofNullable(id), sse.getId());
    }
}