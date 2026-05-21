import asyncio
import json
import logging

import aio_pika

from src.config import settings
from src.email_service import send_email

logger = logging.getLogger(__name__)

_reset_consumer_running = False


def is_reset_consumer_running() -> bool:
    return _reset_consumer_running


async def _connect_with_retry(service_name: str):
    """Conectar a RabbitMQ con reintentos."""
    for attempt in range(10):
        try:
            connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
            logger.info("[%s] Conectado a RabbitMQ (intento %d/10)", service_name, attempt + 1)
            return connection
        except Exception as e:
            logger.warning("[%s] Intento %d/10 fallido: %s", service_name, attempt + 1, str(e))
            await asyncio.sleep(3)
    logger.error("[%s] No se pudo conectar a RabbitMQ tras 10 intentos", service_name)
    return None


async def _procesar_reset_solicitado(evento: dict) -> None:
    """Procesa un evento de solicitud de recuperacion de contrasena y envia email con el codigo."""
    payload = evento.get("payload", evento)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    codigo = payload.get("codigo", "")
    expira_minutos = payload.get("expiraMinutos", 5)

    if not email or not codigo:
        logger.warning("[reset-solicitado] Evento sin email o codigo, ignorando")
        return

    logger.info("[reset-solicitado] Enviando codigo de recuperacion a %s", email)

    await send_email(
        to=email,
        subject="Recuperacion de Contrasena - Codigo de Verificacion",
        template="recuperacion.html",
        context={
            "nombre": nombre,
            "codigo": codigo,
            "expira_minutos": expira_minutos,
        },
    )
    logger.info("[reset-solicitado] Email de recuperacion enviado a %s", email)


async def _on_message(message: aio_pika.IncomingMessage) -> None:
    """Callback para procesar mensajes de RabbitMQ de eventos de recuperacion."""
    async with message.process(ignore_processed=True):
        try:
            body = json.loads(message.body.decode())
            event_type = body.get("eventType", "")

            if event_type == "cuenta.reset-solicitado":
                await _procesar_reset_solicitado(body)
            else:
                logger.warning("[reset-consumer] Evento desconocido: %s", event_type)
        except json.JSONDecodeError as e:
            logger.error("[reset-consumer] Error decodificando JSON: %s", str(e))
        except Exception as e:
            logger.error("[reset-consumer] Error procesando mensaje: %s", str(e))


async def _crear_exchange_y_queue(channel, exchange_name, exchange_type, queue_name, routing_key):
    """Declara exchange y queue, y los bindea."""
    exchange = await channel.declare_exchange(exchange_name, exchange_type, durable=True)
    queue = await channel.declare_queue(queue_name, durable=True)
    await queue.bind(exchange, routing_key=routing_key)
    return exchange, queue


async def start_reset_consumer():
    """Consumer para notif.cuenta.reset-solicitado."""
    global _reset_consumer_running
    connection = await _connect_with_retry("reset-consumer")
    if not connection:
        return
    try:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)

        _, queue = await _crear_exchange_y_queue(
            channel,
            "cuentas.exchange",
            aio_pika.ExchangeType.TOPIC,
            "notif.cuenta.reset-solicitado",
            "cuenta.reset-solicitado"
        )

        _reset_consumer_running = True
        logger.info("[reset-consumer] Consumer iniciado, esperando mensajes...")

        consumer_tag = await queue.consume(_on_message)
        await asyncio.Future()
    except Exception as e:
        logger.error("[reset-consumer] Consumer caido: %s", str(e))
    finally:
        _reset_consumer_running = False
        if connection and not connection.is_closed:
            await connection.close()
