package com.wgzhao.addax.admin.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.io.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Configuration
class RedisConfig {
    @Bean
    fun redisTemplate(connectionFactory: LettuceConnectionFactory?): RedisTemplate<String?, Serializable?> {
        val template = RedisTemplate<String?, Serializable?>()

        // 使用 GenericFastJsonRedisSerializer 替换默认序列化
        //这里覆盖默认的ObjectMapper
        val genericFastJsonRedisSerializer = GenericJackson2JsonRedisSerializer(this.objectMapper)
        // 设置key和value的序列化规则
        template.setKeySerializer(StringRedisSerializer())
        template.setValueSerializer(genericFastJsonRedisSerializer)
        // 设置hashKey和hashValue的序列化规则
        template.setHashKeySerializer(StringRedisSerializer())
        template.setHashValueSerializer(genericFastJsonRedisSerializer)
        template.setConnectionFactory(connectionFactory)
        template.afterPropertiesSet()

        return template
    }

    val objectMapper: ObjectMapper
        get() {
            val mapper = ObjectMapper()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            val javaTimeModule = JavaTimeModule()
            javaTimeModule.addDeserializer<LocalDateTime?>(LocalDateTime::class.java, LocalDateTimeDeserializer(DateTimeFormatter.ISO_DATE_TIME))
            mapper.registerModule(javaTimeModule)
            mapper.registerModule(Jdk8Module())
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            mapper.setTimeZone(Calendar.getInstance().getTimeZone())
            mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
            return mapper
        }
}
