package com.example.medical.common.config;

import com.example.medical.common.annotation.PhiField;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhiMaskingRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper mapper;

    public PhiMaskingRedisSerializer() {
        this.mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config, BeanDescription beanDesc,
                    List<BeanPropertyWriter> beanProperties) {
                List<BeanPropertyWriter> modified = new ArrayList<>();
                for (BeanPropertyWriter writer : beanProperties) {
                    if (writer.getAnnotation(PhiField.class) != null) {
                        modified.add(new PhiRedactingWriter(writer));
                    } else {
                        modified.add(writer);
                    }
                }
                return modified;
            }
        });
        mapper.registerModule(module);
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) return new byte[0];
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return mapper.readValue(bytes, Object.class);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize", e);
        }
    }

    private static class PhiRedactingWriter extends BeanPropertyWriter {
        private final BeanPropertyWriter delegate;

        PhiRedactingWriter(BeanPropertyWriter delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen,
                                     SerializerProvider prov) throws Exception {
            Object value = delegate.get(bean);
            if (value != null) {
                gen.writeFieldName(delegate.getName());
                gen.writeString("[PHI-REDACTED]");
            }
            // null values are omitted by default with NON_NULL
        }
    }
}
