package messaging

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/models"
)

const (
	exchangeName = "vacaciones.exchange"
	exchangeType = "topic"
	maxRetries   = 5
	retryDelay   = 3 * time.Second
)

type RabbitPublisher interface {
	PublishVacacionProgramada(vacacion *models.Vacacion, email, nombre string) error
	IsConnected() bool
	Close() error
}

type rabbitPublisher struct {
	conn    *amqp.Connection
	channel *amqp.Channel
	logger  zerolog.Logger
}

func NewRabbitPublisher(rabbitMQURL string, logger zerolog.Logger) (RabbitPublisher, error) {
	var conn *amqp.Connection
	var err error

	for attempt := 1; attempt <= maxRetries; attempt++ {
		conn, err = amqp.Dial(rabbitMQURL)
		if err == nil {
			break
		}
		logger.Warn().
			Int("attempt", attempt).
			Int("maxRetries", maxRetries).
			Err(err).
			Msg("Error conectando a RabbitMQ, reintentando...")
		if attempt < maxRetries {
			time.Sleep(retryDelay)
		}
	}

	if err != nil {
		return nil, fmt.Errorf("error conectando a RabbitMQ despues de %d intentos: %w", maxRetries, err)
	}

	ch, err := conn.Channel()
	if err != nil {
		conn.Close()
		return nil, fmt.Errorf("error abriendo canal RabbitMQ: %w", err)
	}

	err = ch.ExchangeDeclare(
		exchangeName,
		exchangeType,
		true,  // durable
		false, // auto-deleted
		false, // internal
		false, // no-wait
		nil,
	)
	if err != nil {
		ch.Close()
		conn.Close()
		return nil, fmt.Errorf("error declarando exchange %s: %w", exchangeName, err)
	}

	logger.Info().
		Str("exchange", exchangeName).
		Str("type", exchangeType).
		Msg("Conexion a RabbitMQ establecida exitosamente")

	return &rabbitPublisher{
		conn:    conn,
		channel: ch,
		logger:  logger,
	}, nil
}

func (r *rabbitPublisher) IsConnected() bool {
	if r.conn == nil || r.conn.IsClosed() {
		return false
	}
	return true
}

func (r *rabbitPublisher) PublishVacacionProgramada(vacacion *models.Vacacion, email, nombre string) error {
	if !r.IsConnected() {
		return fmt.Errorf("RabbitMQ no esta conectado")
	}

	payload := models.VacacionEventPayload{
		VacacionID:  vacacion.ID.String(),
		EmpleadoID:  vacacion.EmpleadoID.String(),
		Email:       email,
		Nombre:      nombre,
		FechaInicio: vacacion.FechaInicio.Format("2006-01-02"),
		FechaFin:    vacacion.FechaFin.Format("2006-01-02"),
	}

	envelope := models.VacacionEventEnvelope{
		EventID:   uuid.New().String(),
		EventType: "vacaciones.programadas",
		Timestamp: time.Now().UTC().Format(time.RFC3339),
		Source:    "gestion-vacaciones",
		Version:   "1.0",
		Payload:   payload,
	}

	body, err := json.Marshal(envelope)
	if err != nil {
		return fmt.Errorf("error serializando evento: %w", err)
	}

	err = r.channel.Publish(
		exchangeName,
		"vacaciones.programadas",
		false, // mandatory
		false, // immediate
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			Timestamp:    time.Now(),
			Body:         body,
		},
	)
	if err != nil {
		r.logger.Error().
			Err(err).
			Str("vacacionId", vacacion.ID.String()).
			Msg("Error publicando evento vacaciones.programadas")
		return fmt.Errorf("error publicando evento: %w", err)
	}

	r.logger.Info().
		Str("eventType", "vacaciones.programadas").
		Str("vacacionId", vacacion.ID.String()).
		Str("empleadoId", vacacion.EmpleadoID.String()).
		Msg("Evento vacaciones.programadas publicado exitosamente")

	return nil
}

func (r *rabbitPublisher) Close() error {
	if r.channel != nil {
		if err := r.channel.Close(); err != nil {
			r.logger.Error().Err(err).Msg("Error cerrando canal RabbitMQ")
		}
	}
	if r.conn != nil {
		if err := r.conn.Close(); err != nil {
			r.logger.Error().Err(err).Msg("Error cerrando conexion RabbitMQ")
			return err
		}
	}
	r.logger.Info().Msg("Conexion RabbitMQ cerrada")
	return nil
}
