import pytest
from unittest.mock import AsyncMock, patch

from src.main import app
from src.email_service import get_stats, reset_stats


@pytest.fixture(autouse=True)
def clean_stats():
    reset_stats()
    yield
    reset_stats()


@pytest.mark.asyncio
async def test_metrics_endpoint(client):
    """Verifica que el endpoint /metrics de Prometheus responde."""
    response = await client.get("/metrics")
    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]


@pytest.mark.asyncio
async def test_health_endpoint_dependencies_down(client):
    """Verifica health cuando dependencias estan down."""
    with patch("src.consumers.cuenta_consumer.is_consumer_running", return_value=False):
        with patch("src.consumers.vacaciones_consumer.is_consumer_running", return_value=False):
            with patch("src.main.check_smtp_connection", new_callable=AsyncMock, return_value=False):
                response = await client.get("/health")
                assert response.status_code == 200
                data = response.json()
                assert data["dependencies"]["rabbitmq"] == "DOWN"
                assert data["dependencies"]["smtp"] == "DOWN"
                assert data["status"] == "DOWN"


@pytest.mark.asyncio
async def test_health_endpoint_rabbitmq_up_smtp_down(client):
    """Verifica health cuando rabbitmq esta up pero smtp down."""
    with patch("src.consumers.cuenta_consumer.is_consumer_running", return_value=True):
        with patch("src.consumers.vacaciones_consumer.is_consumer_running", return_value=False):
            with patch("src.main.check_smtp_connection", new_callable=AsyncMock, return_value=False):
                response = await client.get("/health")
                assert response.status_code == 200
                data = response.json()
                assert data["dependencies"]["rabbitmq"] == "UP"
                assert data["dependencies"]["smtp"] == "DOWN"


@pytest.mark.asyncio
async def test_stats_after_sending(client):
    """Verifica que stats reflejan envios."""
    from src.email_service import _stats
    _stats["enviados"] = 5
    _stats["errores"] = 2

    response = await client.get("/notifications/stats")
    assert response.status_code == 200
    data = response.json()
    assert data["enviados"] == 5
    assert data["errores"] == 2
