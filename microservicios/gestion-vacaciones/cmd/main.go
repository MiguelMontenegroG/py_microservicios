package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/empresa/gestion-vacaciones/internal/config"
	"github.com/empresa/gestion-vacaciones/internal/database"
	"github.com/empresa/gestion-vacaciones/internal/handlers"
	"github.com/empresa/gestion-vacaciones/internal/messaging"
	"github.com/empresa/gestion-vacaciones/internal/repository"
	"github.com/empresa/gestion-vacaciones/internal/service"
	"github.com/empresa/gestion-vacaciones/pkg/middleware"
)

func main() {
	cfg := config.Load()
	logger := config.SetupLogger()

	logger.Info().
		Str("port", cfg.Port).
		Str("dbHost", cfg.DBHost).
		Str("rabbitMQ", cfg.RabbitMQURL).
		Msg("Iniciando servicio gestion-vacaciones")

	// Conectar a PostgreSQL
	db, err := database.Connect(cfg, logger)
	if err != nil {
		logger.Fatal().Err(err).Msg("Error conectando a la base de datos")
	}
	defer db.Close()

	// Ejecutar migraciones
	if err := database.RunMigrations(db, logger); err != nil {
		logger.Fatal().Err(err).Msg("Error ejecutando migraciones")
	}

	// Conectar a RabbitMQ
	publisher, err := messaging.NewRabbitPublisher(cfg.RabbitMQURL, logger)
	if err != nil {
		logger.Fatal().Err(err).Msg("Error conectando a RabbitMQ")
	}
	defer publisher.Close()

	// Inicializar dependencias
	vacacionesRepo := repository.NewVacacionesRepository(db, logger)
	vacacionesSvc := service.NewVacacionesService(vacacionesRepo, publisher, logger)
	vacacionesHandler := handlers.NewVacacionesHandler(vacacionesSvc, db, logger, publisher)

	// Configurar router Gin
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(gin.Recovery())
	router.Use(middleware.LoggerMiddleware(logger))
	router.Use(corsMiddleware())

	// Rutas
	router.GET("/health", vacacionesHandler.Health)
	router.GET("/metrics", vacacionesHandler.PrometheusHandler())

	api := router.Group("/")
	{
		api.POST("/vacations", vacacionesHandler.CreateVacacion)
		api.GET("/vacations", vacacionesHandler.ListVacaciones)
		api.GET("/vacations/:id", vacacionesHandler.GetVacacion)
		api.DELETE("/vacations/:id", vacacionesHandler.CancelVacacion)
	}

	// Servidor HTTP con graceful shutdown
	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		logger.Info().Str("port", cfg.Port).Msg("Servidor HTTP iniciado")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal().Err(err).Msg("Error iniciando servidor HTTP")
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logger.Info().Msg("Apagando servidor...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}

	logger.Info().Msg("Servidor apagado correctamente")
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization, X-Empleado-Id, X-Rol")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}
