package io.confluent.kafka.streams.serdes.avro;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/**
 * Compatibility shim for galaxio's gatling-kafka-plugin.
 *
 * The plugin eagerly instantiates io.confluent...GenericAvroSerde at startup,
 * even when tests only use String serializers and never touch Avro.
 *
 * We intentionally do not pull full Confluent Avro dependencies for this test
 * project; if Avro paths are used, fail fast with a clear error.
 */
public final class GenericAvroSerde implements Serde<Object> {

    private static final String ERROR = "Avro serde is not enabled in this project. "
            + "Use String serializers or add Confluent Avro dependencies intentionally.";

    private final Serializer<Object> serializer = new Serializer<>() {
        @Override
        public byte[] serialize(String topic, Object data) {
            throw new UnsupportedOperationException(ERROR);
        }
    };

    private final Deserializer<Object> deserializer = new Deserializer<>() {
        @Override
        public Object deserialize(String topic, byte[] data) {
            throw new UnsupportedOperationException(ERROR);
        }
    };

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Intentionally no-op: this shim is never meant to actively serialize/deserialize.
    }

    @Override
    public Serializer<Object> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<Object> deserializer() {
        return deserializer;
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
