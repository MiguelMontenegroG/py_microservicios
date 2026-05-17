package middleware

import (
	"time"

	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog"
)

func LoggerMiddleware(logger zerolog.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		path := c.Request.URL.Path

		c.Next()

		latency := time.Since(start)
		status := c.Writer.Status()
		clientIP := c.ClientIP()
		method := c.Request.Method

		logger.Info().
			Str("method", method).
			Str("path", path).
			Int("status", status).
			Str("clientIP", clientIP).
			Dur("latency", latency).
			Int("bodySize", c.Writer.Size()).
			Msg("request")
	}
}
