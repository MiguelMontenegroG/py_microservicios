package config

import (
	"os"
	"time"

	"github.com/rs/zerolog"
)

type Config struct {
	DBHost       string
	DBPort       string
	DBName       string
	DBUser       string
	DBPassword   string
	RabbitMQURL  string
	Port         string
}

func Load() *Config {
	return &Config{
		DBHost:      getEnv("DB_HOST", "localhost"),
		DBPort:      getEnv("DB_PORT", "5432"),
		DBName:      getEnv("DB_NAME", "vacaciones_db"),
		DBUser:      getEnv("DB_USER", "vacaciones_user"),
		DBPassword:  getEnv("DB_PASSWORD", "vacaciones_pass"),
		RabbitMQURL: getEnv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672"),
		Port:        getEnv("PORT", "8084"),
	}
}

func (c *Config) DSN() string {
	return "host=" + c.DBHost +
		" port=" + c.DBPort +
		" user=" + c.DBUser +
		" password=" + c.DBPassword +
		" dbname=" + c.DBName +
		" sslmode=disable"
}

func getEnv(key, defaultVal string) string {
	if val, ok := os.LookupEnv(key); ok {
		return val
	}
	return defaultVal
}

func SetupLogger() zerolog.Logger {
	output := zerolog.NewConsoleWriter(func(w *zerolog.ConsoleWriter) {
		w.TimeFormat = time.RFC3339
	})

	logger := zerolog.New(output).
		With().
		Timestamp().
		Str("service", "gestion-vacaciones").
		Logger()

	return logger
}
