import json
import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from src.consumers.cuenta_consumer import _procesar_cuenta_activada, _procesar_cuenta_desactivada
from src.consumers.vacaciones_consumer import _procesar_vacaciones_programadas
from src.email_service import reset_stats


@pytest.fixture(autouse=True)
def clean_stats():
    reset_stats()
    yield
    reset_stats()


# --- Tests cuenta_consumer ---

@pytest.mark.asyncio
async def test_procesar_cuenta_activada_con_credenciales():
    """Verifica que cuenta.activada envia email con credenciales cuando esPrimerAcceso=true."""
    evento = {
        "eventType": "cuenta.activada",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "passwordTemporal": "Abc12345",
            "esPrimerAcceso": True,
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_activada(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["to"] == "juan@empresa.com"
    assert kwargs["template"] == "bienvenida.html"
    assert kwargs["context"]["nombre"] == "Juan Perez"
    assert kwargs["context"]["username"] == "juan@empresa.com"
    assert kwargs["context"]["passwordTemporal"] == "Abc12345"


@pytest.mark.asyncio
async def test_procesar_cuenta_activada_primer_acceso_false():
    """Verifica que NO envia email si esPrimerAcceso=false."""
    evento = {
        "eventType": "cuenta.activada",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "passwordTemporal": "Abc12345",
            "esPrimerAcceso": False,
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_activada(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_procesar_cuenta_activada_sin_email():
    """Verifica que no falla si falta el email."""
    evento = {
        "eventType": "cuenta.activada",
        "payload": {
            "empleadoId": "uuid",
            "nombre": "Juan",
            "esPrimerAcceso": True,
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_activada(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_procesar_cuenta_desactivada():
    """Verifica que cuenta.desactivada envia email de notificacion."""
    evento = {
        "eventType": "cuenta.desactivada",
        "payload": {
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "motivo": "RETIRO",
            "timestamp": "2024-06-01T09:00:00Z",
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_desactivada(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["to"] == "juan@empresa.com"
    assert kwargs["template"] == "desactivacion.html"
    assert kwargs["context"]["motivo"] == "RETIRO"
    assert kwargs["context"]["timestamp"] == "2024-06-01T09:00:00Z"


@pytest.mark.asyncio
async def test_procesar_cuenta_desactivada_sin_email():
    """Verifica que no falla si falta email en desactivacion."""
    evento = {
        "eventType": "cuenta.desactivada",
        "payload": {
            "empleadoId": "uuid",
            "nombre": "Juan",
            "motivo": "RETIRO",
        },
    }

    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_cuenta_desactivada(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_cuenta_consumer_payload_malformado_no_excepcion():
    """Verifica que payload malformado no lanza excepcion."""
    with patch("src.consumers.cuenta_consumer.send_email", new_callable=AsyncMock):
        await _procesar_cuenta_activada({"eventType": "cuenta.activada", "payload": {}})
        await _procesar_cuenta_desactivada({"eventType": "cuenta.desactivada", "payload": {}})


# --- Tests vacaciones_consumer ---

@pytest.mark.asyncio
async def test_procesar_vacaciones_programadas():
    """Verifica que vacaciones.programadas envia email de confirmacion."""
    evento = {
        "eventType": "vacaciones.programadas",
        "payload": {
            "vacacionId": "550e8400-e29b-41d4-a716-446655440010",
            "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
            "email": "juan@empresa.com",
            "nombre": "Juan Perez",
            "fechaInicio": "2024-07-01",
            "fechaFin": "2024-07-15",
        },
    }

    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_vacaciones_programadas(evento)

    mock_send.assert_called_once()
    args, kwargs = mock_send.call_args
    assert kwargs["to"] == "juan@empresa.com"
    assert kwargs["template"] == "vacaciones.html"
    assert kwargs["context"]["nombre"] == "Juan Perez"
    assert kwargs["context"]["fechaInicio"] == "2024-07-01"
    assert kwargs["context"]["fechaFin"] == "2024-07-15"
    assert kwargs["context"]["dias_totales"] == 15


@pytest.mark.asyncio
async def test_procesar_vacaciones_sin_email():
    """Verifica que no falla si falta email en vacaciones."""
    evento = {
        "eventType": "vacaciones.programadas",
        "payload": {
            "empleadoId": "uuid",
            "nombre": "Juan",
            "fechaInicio": "2024-07-01",
            "fechaFin": "2024-07-15",
        },
    }

    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock) as mock_send:
        await _procesar_vacaciones_programadas(evento)

    mock_send.assert_not_called()


@pytest.mark.asyncio
async def test_vacaciones_consumer_payload_malformado_no_excepcion():
    """Verifica que payload malformado en vacaciones no lanza excepcion."""
    with patch("src.consumers.vacaciones_consumer.send_email", new_callable=AsyncMock):
        await _procesar_vacaciones_programadas({"eventType": "vacaciones.programadas", "payload": {}})


# --- Tests de integracion de health check ---

@pytest.mark.asyncio
async def test_health_endpoint(client):
    """Verifica que el health endpoint responde."""
    response = await client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "service" in data
    assert data["service"] == "notificaciones"
    assert "dependencies" in data
    assert "rabbitmq" in data["dependencies"]
    assert "smtp" in data["dependencies"]


@pytest.mark.asyncio
async def test_stats_endpoint(client):
    """Verifica que el endpoint de estadisticas responde."""
    response = await client.get("/notifications/stats")
    assert response.status_code == 200
    data = response.json()
    assert "enviados" in data
    assert "errores" in data
