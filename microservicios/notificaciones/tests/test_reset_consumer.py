import json
import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from src.consumers.reset_consumer import (
    _procesar_reset_solicitado,
    _on_message as reset_on_message,
    is_reset_consumer_running,
)
from src.email_service import reset_stats


@pytest.fixture(autouse=True)
def clean_stats():
    reset_stats()
    yield
    reset_stats()


# --- Tests de is_consumer_running ---

def test_reset_is_consumer_running_default():
    """Verifica que is_reset_consumer_running retorna False inicialmente."""
    assert is_reset_consumer_running() is False


# --- Tests de _procesar_reset_solicitado ---

@pytest.mark.asyncio
async def test_procesar_reset_solicitado_exitoso():
    """Verifica que reset-solicitado envia email con codigo de recuperacion."""
    evento = {
        "eventType": "cuenta.reset-solicitado",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "codigo": "482913",
            "expiraMinutos": 5,
        },
    }

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_reset_solicitado(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["to"] == "juan@empresa.com"
    assert kwargs["template"] == "recuperacion.html"
    assert kwargs["context"]["nombre"] == "Juan Perez"
    assert kwargs["context"]["codigo"] == "482913"
    assert kwargs["context"]["expira_minutos"] == 5


@pytest.mark.asyncio
async def test_procesar_reset_solicitado_sin_email():
    """Verifica que no falla si falta email."""
    evento = {
        "eventType": "cuenta.reset-solicitado",
        "payload": {
            "empleadoId": "uuid",
            "nombre": "Juan",
            "codigo": "482913",
        },
    }

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_reset_solicitado(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_procesar_reset_solicitado_sin_codigo():
    """Verifica que no falla si falta codigo."""
    evento = {
        "eventType": "cuenta.reset-solicitado",
        "payload": {
            "empleadoId": "uuid",
            "email": "juan@empresa.com",
            "nombre": "Juan",
        },
    }

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_reset_solicitado(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_procesar_reset_solicitado_payload_vacio():
    """Verifica que payload vacio no causa error."""
    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_reset_solicitado({"eventType": "cuenta.reset-solicitado", "payload": {}})

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_procesar_reset_solicitado_expira_minutos_default():
    """Verifica que si falta expiraMinutos usa valor por defecto 5."""
    evento = {
        "eventType": "cuenta.reset-solicitado",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "codigo": "482913",
        },
    }

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_reset_solicitado(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["context"]["expira_minutos"] == 5


# --- Tests de _on_message ---

def _make_mock_message(body: dict):
    """Crea un mock de aio_pika.IncomingMessage para pruebas."""
    msg = MagicMock()
    msg.body = json.dumps(body).encode()
    process_cm = MagicMock()
    process_cm.__aenter__ = AsyncMock()
    process_cm.__aexit__ = AsyncMock()
    msg.process = MagicMock(return_value=process_cm)
    return msg


@pytest.mark.asyncio
async def test_reset_on_message_evento_desconocido():
    """Verifica que evento desconocido no lanza excepcion."""
    msg = _make_mock_message({"eventType": "evento.desconocido", "payload": {}})
    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock):
        await reset_on_message(msg)


@pytest.mark.asyncio
async def test_reset_on_message_json_invalido():
    """Verifica que JSON invalido no lanza excepcion."""
    msg = MagicMock()
    msg.body = b"json-invalido{{{"
    process_cm = MagicMock()
    process_cm.__aenter__ = AsyncMock()
    process_cm.__aexit__ = AsyncMock()
    msg.process = MagicMock(return_value=process_cm)

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock):
        await reset_on_message(msg)


@pytest.mark.asyncio
async def test_reset_on_message_exitoso():
    """Verifica el flujo completo de on_message para cuenta.reset-solicitado."""
    msg = _make_mock_message({
        "eventType": "cuenta.reset-solicitado",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "codigo": "482913",
            "expiraMinutos": 5,
        },
    })

    with patch("src.consumers.reset_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await reset_on_message(msg)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["to"] == "juan@empresa.com"
    assert kwargs["template"] == "recuperacion.html"
    assert kwargs["context"]["codigo"] == "482913"
