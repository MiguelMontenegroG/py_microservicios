import json
import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from src.consumers.cuenta_consumer import is_consumer_running as cuenta_is_running
from src.consumers.vacaciones_consumer import is_consumer_running as vacaciones_is_running
from src.consumers.cuenta_consumer import _procesar_cuenta_activada, _procesar_cuenta_desactivada, _on_message as cuenta_on_message
from src.consumers.vacaciones_consumer import _procesar_vacaciones_programadas, _on_message as vacaciones_on_message
from src.email_service import reset_stats


@pytest.fixture(autouse=True)
def clean_stats():
    reset_stats()
    yield
    reset_stats()


# --- Tests de is_consumer_running ---

def test_cuenta_is_consumer_running_default():
    """Verifica que is_consumer_running retorna False inicialmente."""
    assert cuenta_is_running() is False


def test_vacaciones_is_consumer_running_default():
    """Verifica que is_consumer_running retorna False inicialmente."""
    assert vacaciones_is_running() is False


# --- Tests de formato de fechas invalidas ---

@pytest.mark.asyncio
async def test_vacaciones_fechas_invalidas():
    """Verifica que fechas invalidas no causan error (dias_totales = '---')."""
    evento = {
        "eventType": "vacaciones.programadas",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan",
            "fechaInicio": "fecha-invalida",
            "fechaFin": "2024-07-15",
        },
    }

    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_vacaciones_programadas(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["context"]["dias_totales"] == "---"


@pytest.mark.asyncio
async def test_vacaciones_fechas_none():
    """Verifica que fechas None no causan error."""
    evento = {
        "eventType": "vacaciones.programadas",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan",
            "fechaInicio": None,
            "fechaFin": None,
        },
    }

    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_vacaciones_programadas(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["context"]["dias_totales"] == "---"


# --- Tests de payload directo (sin eventType wrapper) ---

@pytest.mark.asyncio
async def test_cuenta_activada_payload_directo():
    """Verifica que el consumer funciona con payload directo (sin wrapper eventType)."""
    payload = {
        "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
        "email": "juan@empresa.com",
        "nombre": "Juan Perez",
        "passwordTemporal": "Abc12345",
        "esPrimerAcceso": True,
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_activada({"eventType": "cuenta.activada", "payload": payload})

    mock_send.assert_called_once()


@pytest.mark.asyncio
async def test_cuenta_desactivada_motivo_vacaciones():
    """Verifica envio de email con motivo VACACIONES."""
    evento = {
        "eventType": "cuenta.desactivada",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "motivo": "VACACIONES",
            "timestamp": "2024-07-01T09:00:00Z",
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_desactivada(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["context"]["motivo"] == "VACACIONES"


# --- Helpers para mockear message de aio_pika ---

def _make_mock_message(body: dict):
    """Crea un mock de aio_pika.IncomingMessage para pruebas."""
    msg = MagicMock()
    msg.body = json.dumps(body).encode()
    # process() debe ser un async context manager
    process_cm = MagicMock()
    process_cm.__aenter__ = AsyncMock()
    process_cm.__aexit__ = AsyncMock()
    msg.process = MagicMock(return_value=process_cm)
    return msg


@pytest.mark.asyncio
async def test_cuenta_on_message_evento_desconocido():
    """Verifica que evento desconocido en cuenta_consumer no lanza excepcion."""
    msg = _make_mock_message({"eventType": "evento.desconocido", "payload": {}})
    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock):
        await cuenta_on_message(msg)


@pytest.mark.asyncio
async def test_cuenta_on_message_json_invalido():
    """Verifica que JSON invalido no lanza excepcion."""
    msg = MagicMock()
    msg.body = b"json-invalido{{{"
    process_cm = MagicMock()
    process_cm.__aenter__ = AsyncMock()
    process_cm.__aexit__ = AsyncMock()
    msg.process = MagicMock(return_value=process_cm)

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock):
        await cuenta_on_message(msg)


@pytest.mark.asyncio
async def test_cuenta_on_message_activada_exitoso():
    """Verifica el flujo completo de on_message para cuenta.activada."""
    msg = _make_mock_message({
        "eventType": "cuenta.activada",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "passwordTemporal": "Abc12345",
            "esPrimerAcceso": True,
        },
    })
    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await cuenta_on_message(msg)
    mock_send.assert_called_once()


@pytest.mark.asyncio
async def test_cuenta_on_message_desactivada_exitoso():
    """Verifica el flujo completo de on_message para cuenta.desactivada."""
    msg = _make_mock_message({
        "eventType": "cuenta.desactivada",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "motivo": "VACACIONES",
            "timestamp": "2024-07-01T09:00:00Z",
        },
    })
    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await cuenta_on_message(msg)
    mock_send.assert_called_once()


@pytest.mark.asyncio
async def test_vacaciones_on_message_evento_desconocido():
    """Verifica que evento desconocido en vacaciones_consumer no lanza excepcion."""
    msg = _make_mock_message({"eventType": "evento.desconocido", "payload": {}})
    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock):
        await vacaciones_on_message(msg)


@pytest.mark.asyncio
async def test_vacaciones_on_message_exitoso():
    """Verifica el flujo completo de on_message para vacaciones.programadas."""
    msg = _make_mock_message({
        "eventType": "vacaciones.programadas",
        "payload": {
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "fechaInicio": "2024-07-01",
            "fechaFin": "2024-07-15",
        },
    })
    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await vacaciones_on_message(msg)
    mock_send.assert_called_once()
