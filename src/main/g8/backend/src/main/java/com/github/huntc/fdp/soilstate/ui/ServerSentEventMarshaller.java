package com.github.huntc.fdp.soilstate.ui;

import akka.http.javadsl.model.sse.ServerSentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.huntc.fdp.soilstate.SoilStateReading;
import com.github.huntc.lora.controlplane.EndDeviceEvents;
import spray.json.SerializationException;

/**
 * Marshals data to server sent events
 */
class ServerSentEventMarshaller {

    static ServerSentEvent toServerSentEvent(EndDeviceEvents.Event event, Long offset) {
        try {
            return ServerSentEvent.create(
                    EndDeviceEvents.EventJsonProtocol\$.MODULE\$.eventFormat().write(event).compactPrint(),
                    event.getClass().getSimpleName(),
                    offset.toString());
        } catch (SerializationException e) {
            // Events unknown to us will pass through to the client and look like a heartbeat
            return ServerSentEvent.create("");
        }
    }

    static ServerSentEvent toServerSentEvent(SoilStateReading reading, Long offset) throws JsonProcessingException {
        return ServerSentEvent.create(
                SoilStateReading.mapper.writeValueAsString(reading),
                reading.getClass().getSimpleName(),
                offset.toString());
    }
}