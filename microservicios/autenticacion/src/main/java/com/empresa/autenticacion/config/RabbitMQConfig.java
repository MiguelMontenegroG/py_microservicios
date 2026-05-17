package com.empresa.autenticacion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange.empleados}")
    private String empleadosExchange;

    @Value("${app.rabbitmq.exchange.vacaciones}")
    private String vacacionesExchange;

    @Value("${app.rabbitmq.exchange.cuentas}")
    private String cuentasExchange;

    @Value("${app.rabbitmq.queue.auth-empleado-creado}")
    private String authEmpleadoCreadoQueue;

    @Value("${app.rabbitmq.queue.auth-empleado-eliminado}")
    private String authEmpleadoEliminadoQueue;

    @Value("${app.rabbitmq.queue.auth-vacaciones-programadas}")
    private String authVacacionesProgramadasQueue;

    // --- Exchanges ---
    @Bean
    public TopicExchange empleadosExchange() {
        return new TopicExchange(empleadosExchange, true, false);
    }

    @Bean
    public TopicExchange vacacionesExchange() {
        return new TopicExchange(vacacionesExchange, true, false);
    }

    @Bean
    public TopicExchange cuentasExchange() {
        return new TopicExchange(cuentasExchange, true, false);
    }

    // --- Queues ---
    @Bean
    public Queue authEmpleadoCreadoQueue() {
        return new Queue(authEmpleadoCreadoQueue, true);
    }

    @Bean
    public Queue authEmpleadoEliminadoQueue() {
        return new Queue(authEmpleadoEliminadoQueue, true);
    }

    @Bean
    public Queue authVacacionesProgramadasQueue() {
        return new Queue(authVacacionesProgramadasQueue, true);
    }

    // --- Bindings ---
    @Bean
    public Binding authEmpleadoCreadoBinding() {
        return BindingBuilder
                .bind(authEmpleadoCreadoQueue())
                .to(empleadosExchange())
                .with("empleado.creado");
    }

    @Bean
    public Binding authEmpleadoEliminadoBinding() {
        return BindingBuilder
                .bind(authEmpleadoEliminadoQueue())
                .to(empleadosExchange())
                .with("empleado.eliminado");
    }

    @Bean
    public Binding authVacacionesProgramadasBinding() {
        return BindingBuilder
                .bind(authVacacionesProgramadasQueue())
                .to(vacacionesExchange())
                .with("vacaciones.programadas");
    }

    // --- Message Converter (Jackson con soporte JSR310) ---
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
