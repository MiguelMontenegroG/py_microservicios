package messaging

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/models"
)

const (
	exchangeName = "vacaciones.exchange"
	exchangeType = "topic"
	retryDelay   = 5 * time.Second
)

type RabbitPublisher interface {
	PublishVacacionProgramada(vacacion *models.Vacacion, email, nombre string) error
	IsConnected() bool
	Close() error
}

type rabbitPublisher struct {
	conn        *amqp.Connection
	channel     *amqp.Channel
	connMu      sync.RWMutex
	rabbitMQURL string
	logger      zerolog.Logger
	connected   bool
	closeCh     chan struct{}
}

func NewRabbitPublisher(rabbitMQURL string, logger zerolog.Logger) (RabbitPublisher, error) {
	p := &rabbitPublisher{
		rabbitMQURL: rabbitMQURL,
		logger:      logger,
		closeCh:     make(chan struct{}),
	}

	// Intentar conexion inicial
	if err := p.connect(); err != nil {
		logger.Warn().
			Err(err).
			Msg("No se pudo conectar a RabbitMQ al iniciar, se iniciara en modo degradado y se reintentara en background")
		// Iniciar goroutine de reconexion en background
		go p.reconnectLoop()
		return p, nil
	}

	logger.Info().
		Str("exchange", exchangeName).
		Str("type", exchangeType).
		Msg("Conexion a RabbitMQ establecida exitosamente")

	return p, nil
}

func (r *rabbitPublisher) connect() error {
	conn, err := amqp.Dial(r.rabbitMQURL)
	if err != nil {
		return fmt.Errorf("error conectando a RabbitMQ: %w", err)
	}

	ch, err := conn.Channel()
	if err != nil {
		conn.Close()
		return fmt.Errorf("error abriendo canal RabbitMQ: %w", err)
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
		return fmt.Errorf("error declarando exchange %s: %w", exchangeName, err)
	}

	r.connMu.Lock()
	defer r.connMu.Unlock()

	// Cerrar conexion anterior si existe
	if r.conn != nil && !r.conn.IsClosed() {
		r.conn.Close()
	}
	if r.channel != nil {
		r.channel.Close()
	}

	r.conn = conn
	r.channel = ch
	r.connected = true

	return nil
}

func (r *rabbitPublisher) reconnectLoop() {
	attempt := 1
	for {
		select {
		case <-r.closeCh:
			return
		default:
			time.Sleep(retryDelay)

			r.connMu.RLock()
			isConnected := r.connected
			r.connMu.RUnlock()

			if isConnected {
				attempt = 1
				continue
			}

			r.logger.Info().
				Int("attempt", attempt).
				Msg("Reintentando conexion a RabbitMQ en background...")

			if err := r.connect(); err != nil {
				r.logger.Warn().
					Int("attempt", attempt).
					Err(err).
					Msg("Reintento de conexion a RabbitMQ fallido")
				attempt++
				continue
			}

			r.logger.Info().
				Int("attempts", attempt).
				Msg("Conexion a RabbitMQ restablecida exitosamente")
			attempt = 1
		}
	}
}

func (r *rabbitPublisher) IsConnected() bool {
	r.connMu.RLock()
	defer r.connMu.RUnlock()
	return r.connected && r.conn != nil && !r.conn.IsClosed()
}

func (r *rabbitPublisher) PublishVacacionProgramada(vacacion *models.Vacacion, email, nombre string) error {
	r.connMu.RLock()
	isConnected := r.connected && r.conn != nil && !r.conn.IsClosed()
	ch := r.channel
	r.connMu.RUnlock()

	if !isConnected || ch == nil {
		r.logger.Warn().
			Str("vacacionId", vacacion.ID.String()).
			Msg("RabbitMQ no conectado, no se puede publicar evento vacaciones.programadas")
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

	err = ch.Publish(
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

		// Si falla la publicacion, marcar como desconectado para reintentar
		r.connMu.Lock()
		r.connected = false
		r.connMu.Unlock()

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
	close(r.closeCh)

	r.connMu.Lock()
	defer r.connMu.Unlock()

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
	r.connected = false
	r.logger.Info().Msg("Conexion RabbitMQ cerrada")
	return nil
}
