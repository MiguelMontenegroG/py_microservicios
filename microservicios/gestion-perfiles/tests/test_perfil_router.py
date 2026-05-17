"""Tests de integracion para el router de perfiles."""
import pytest
import pytest_asyncio
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from httpx import AsyncClient, ASGITransport

# Parchear get_collection ANTES de importar src
_mock_collection = AsyncMock()

_patcher_db = patch("src.database.get_collection", return_value=_mock_collection)
_patcher_db.start()

# Importar app usando importlib
import importlib
src_main = importlib.import_module("src.main")
app = src_main.app

# Ahora que src.routers.perfil_router esta cargado, parchear is_consumer_running
src_routers_perfil = importlib.import_module("src.routers.perfil_router")
_patcher_health = patch.object(src_routers_perfil, "is_consumer_running", return_value=True)
_patcher_health.start()


@pytest.fixture(autouse=True)
def reset_mock():
    _mock_collection.reset_mock()
    yield


@pytest.fixture
async def async_client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


@pytest.mark.asyncio
async def test_health_check(async_client):
    _mock_collection.count_documents = AsyncMock(return_value=5)

    response = await async_client.get("/health")

    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert data["service"] == "gestion-perfiles"
    assert data["dependencies"]["mongodb"] == "UP"


@pytest.mark.asyncio
async def test_crear_perfil_exitoso(async_client):
    _mock_collection.insert_one = AsyncMock()

    payload = {
        "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
    }

    response = await async_client.post("/profiles", json=payload)

    assert response.status_code == 201
    data = response.json()
    assert data["success"] is True
    assert data["data"]["empleadoId"] == "550e8400-e29b-41d4-a716-446655440001"
    assert data["data"]["archivado"] is False
    _mock_collection.insert_one.assert_awaited_once()


@pytest.mark.asyncio
async def test_crear_perfil_duplicado(async_client):
    _mock_collection.insert_one = AsyncMock(
        side_effect=Exception("E11000 duplicate key error collection")
    )

    payload = {
        "empleadoId": "550e8400-e29b-41d4-a716-446655440001",
        "email": "juan.perez@empresa.com",
    }

    response = await async_client.post("/profiles", json=payload)

    assert response.status_code == 409
    data = response.json()
    assert data["detail"]["error"]["code"] == "PERFIL_DUPLICADO"


@pytest.mark.asyncio
async def test_obtener_perfil_exitoso(async_client):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    now = datetime.now(timezone.utc)

    _mock_collection.find_one = AsyncMock(return_value={
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": None,
        "biografia": "Desarrollador",
        "telefono": None,
        "direccion": {"calle": None, "ciudad": None, "codigoPostal": None, "pais": None},
        "redesSociales": {"linkedin": None, "github": None},
        "archivado": False,
        "createdAt": now,
        "updatedAt": now,
    })

    response = await async_client.get(f"/profiles/{empleado_id}")

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["data"]["empleadoId"] == empleado_id
    assert data["data"]["nombre"] == "Juan"
    _mock_collection.find_one.assert_awaited_once_with({"empleadoId": empleado_id})


@pytest.mark.asyncio
async def test_obtener_perfil_no_encontrado(async_client):
    _mock_collection.find_one = AsyncMock(return_value=None)

    response = await async_client.get("/profiles/00000000-0000-0000-0000-000000000000")

    assert response.status_code == 404
    data = response.json()
    assert data["detail"]["error"]["code"] == "PERFIL_NO_ENCONTRADO"


@pytest.mark.asyncio
async def test_actualizar_perfil_exitoso(async_client):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    now = datetime.now(timezone.utc)

    _mock_collection.find_one_and_update = AsyncMock(return_value={
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": "https://fotos.com/nueva.jpg",
        "biografia": "Nueva biografia",
        "telefono": "+52 55 9999 9999",
        "direccion": {"calle": "Nueva Calle 456", "ciudad": "CDMX", "codigoPostal": "06700", "pais": "Mexico"},
        "redesSociales": {"linkedin": "https://linkedin.com/in/juanperez", "github": "https://github.com/juanperez"},
        "archivado": False,
        "createdAt": now,
        "updatedAt": now,
    })

    payload = {
        "foto": "https://fotos.com/nueva.jpg",
        "biografia": "Nueva biografia",
        "telefono": "+52 55 9999 9999",
    }

    response = await async_client.put(
        f"/profiles/{empleado_id}",
        json=payload,
        headers={"X-Empleado-Id": empleado_id},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["data"]["foto"] == "https://fotos.com/nueva.jpg"


@pytest.mark.asyncio
async def test_actualizar_perfil_acceso_denegado(async_client):
    payload = {"biografia": "Nueva biografia"}

    response = await async_client.put(
        "/profiles/550e8400-e29b-41d4-a716-446655440001",
        json=payload,
        headers={"X-Empleado-Id": "660e8400-e29b-41d4-a716-446655440002"},
    )

    assert response.status_code == 403
    data = response.json()
    assert data["detail"]["error"]["code"] == "ACCESO_DENEGADO"


@pytest.mark.asyncio
async def test_actualizar_perfil_no_encontrado(async_client):
    _mock_collection.find_one_and_update = AsyncMock(return_value=None)
    empleado_id = "00000000-0000-0000-0000-000000000000"

    response = await async_client.put(
        f"/profiles/{empleado_id}",
        json={"biografia": "Test"},
        headers={"X-Empleado-Id": empleado_id},
    )

    assert response.status_code == 404
    data = response.json()
    assert data["detail"]["error"]["code"] == "PERFIL_NO_ENCONTRADO"


@pytest.mark.asyncio
async def test_actualizar_perfil_sin_header(async_client):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    now = datetime.now(timezone.utc)

    _mock_collection.find_one_and_update = AsyncMock(return_value={
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": None,
        "biografia": "Test sin header",
        "telefono": None,
        "direccion": {"calle": None, "ciudad": None, "codigoPostal": None, "pais": None},
        "redesSociales": {"linkedin": None, "github": None},
        "archivado": False,
        "createdAt": now,
        "updatedAt": now,
    })

    response = await async_client.put(
        f"/profiles/{empleado_id}",
        json={"biografia": "Test sin header"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True