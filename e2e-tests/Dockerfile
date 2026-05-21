FROM node:20-alpine

WORKDIR /app

# Copiar archivos de dependencias
COPY package.json package-lock.json* ./

# Instalar dependencias
RUN npm ci --quiet

# Copiar el resto del proyecto
COPY . .

# No usar .env en produccion, las variables vienen del entorno
RUN cp env.example .env 2>/dev/null || true

# Crear directorio para reportes
RUN mkdir -p reports

# Comando por defecto: ejecutar tests y generar reporte HTML
CMD ["npm", "test"]
