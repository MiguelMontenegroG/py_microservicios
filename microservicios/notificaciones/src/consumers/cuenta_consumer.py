import asyncio
import json
import logging
from typing import Optional

import aio_pika

from src.config import settings
from src.email_service import send_email

logger = logging.getLogger(__name__)

_running = False


def is_consumer_running() -> bool:
    return _running


async def _procesar_cuenta_activada(body: dict) -> None:
    """Procesa evento cuenta.activada: envia email de bienvenida con credenciales."""
    payload = body.get("payload", body)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    username = payload.get("email", "---")
    password_temporal = payload.get("passwordTemporal", "")
    es_primer_acceso = payload.get("esPrimerAcceso", False)

    if not email:
        logger.warning("Evento cuenta.activada sin email, ignorando")
        return

    if not es_primer_acceso:
        logger.info("Evento cuenta.activada con esPrimerAcceso=false para %s, omitiendo email", email)
        return

    context = {
        "nombre": nombre,
        "username": username,
        "passwordTemporal": password_temporal,
        "url_acceso": "http://localhost:8080",
    }

    await send_email(
        to=email,
        subject="Bienvenido a la Empresa - Credenciales de Acceso",
        template="bienvenida.html",
        context=context,
    )


async def _procesar_cuenta_desactivada(body: dict) -> None:
    """Procesa evento cuenta.desactivada: envia email de notificacion."""
    payload = body.get("payload", body)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    motivo = payload.get("motivo", "No especificado")
    timestamp = payload.get("timestamp", "")

    if not email:
        logger.warning("Evento cuenta.desactivada sin email, ignorando")
        return

    context = {
        "nombre": nombre,
        "motivo": motivo,
        "timestamp": timestamp,
    }

    await send_email(
        to=email,
        subject="Notificacion: Cuenta Desactivada",
        template="desactivacion.html",
        context=context,
    )


_handlers = {
    "cuenta.activada": _procesar_cuenta_activada,
    "cuenta.desactivada": _procesar_cuenta_desactivada,
}


async def _on_message(message: aio_pika.IncomingMessage) -> None:
    async with message.process(ignore_processed=True):
        try:
            body = json.loads(message.body.decode())
            event_type = body.get("eventType", "")
            logger.info("Evento recibido: %s", event_type)

            handler = _handlers.get(event_type)
            if handler:
                await handler(body)
            else:
                logger.warning("Tipo de evento desconocido: %s", event_type)
        except json.JSONDecodeError as e:
            logger.error("Error decodificando JSON: %s", str(e))
        except Exception as e:
            logger.error("Error procesando mensaje: %s", str(e))


async def start_cuenta_consumer() -> None:
    """Inicia el consumidor de eventos de cuenta (activada/desactivada)."""
    global _running

    connection: Optional[aio_pika.Connection] = None
    for attempt in range(10):
        try:
            connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
            logger.info("Conectado a RabbitMQ (intento %d/10)", attempt + 1)
            break
        except Exception as e:
            logger.warning(
                "Intento %d/10: no se pudo conectar a RabbitMQ: %s",
                attempt + 1, str(e),
            )
            await asyncio.sleep(3)
    else:
        logger.error("No se pudo conectar a RabbitMQ tras 10 intentos")
        return

    try:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=10)

        exchange = await channel.get_exchange("cuentas.exchange")

        # Declarar colas y bindings
        queue_activada = await channel.declare_queue("notif.cuenta.activada", durable=True)
        await queue_activada.bind(exchange, routing_key="cuenta.activada")

        queue_desactivada = await channel.declare_queue("notif.cuenta.desactivada", durable=True)
        await queue_desactivada.bind(exchange, routing_key="cuenta.desactivada")

        _running = True
        logger.info("Consumer de cuentas iniciado - esperando mensajes...")

        await asyncio.gather(
            queue_activada.consume(_on_message),
            queue_desactivada.consume(_on_message),
        )
    except Exception as e:
        logger.error("Error en consumer de cuentas: %s", str(e))
    finally:
        if connection and not connection.is_closed:
            await connection.close()
        _running = False
