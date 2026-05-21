import logging
from datetime import datetime, timezone
from typing import Optional

from src.database import get_collection
from src.models.perfil import PerfilDB, Direccion, RedesSociales
from src.schemas.perfil_schema import PerfilCreate, PerfilUpdate

logger = logging.getLogger("gestion-perfiles")


class PerfilService:

    @staticmethod
    async def crear_perfil(data: PerfilCreate) -> PerfilDB:
        """Crear un perfil vacio para un nuevo empleado (desde eventos)"""
        collection = get_collection()
        perfil = PerfilDB(
            empleadoId=data.empleadoId,
            email=data.email,
            nombre=data.nombre,
            apellido=data.apellido,
        )
        await collection.insert_one(perfil.to_mongo_dict())
        logger.info(f"Perfil creado para empleado {data.empleadoId}")
        return perfil

    @staticmethod
    async def obtener_perfil(empleado_id: str) -> Optional[PerfilDB]:
        """Obtener perfil por empleadoId"""
        collection = get_collection()
        doc = await collection.find_one({"empleadoId": empleado_id})
        if doc:
            doc.pop("_id", None)
            return PerfilDB(**doc)
        return None

    @staticmethod
    async def actualizar_perfil(empleado_id: str, data: PerfilUpdate) -> Optional[PerfilDB]:
        """Actualizar perfil existente"""
        collection = get_collection()

        update_data = data.model_dump(exclude_none=True)
        if not update_data:
            # No hay campos para actualizar, retornar perfil actual
            return await PerfilService.obtener_perfil(empleado_id)

        update_data["updatedAt"] = datetime.now(timezone.utc)

        result = await collection.find_one_and_update(
            {"empleadoId": empleado_id},
            {"$set": update_data},
            return_document=True,
        )
        if result:
            result.pop("_id", None)
            return PerfilDB(**result)
        return None

    @staticmethod
    async def archivar_perfil(empleado_id: str) -> Optional[PerfilDB]:
        """Archivar perfil (soft delete - desde eventos)"""
        collection = get_collection()
        result = await collection.find_one_and_update(
            {"empleadoId": empleado_id},
            {"$set": {
                "archivado": True,
                "updatedAt": datetime.now(timezone.utc),
            }},
            return_document=True,
        )
        if result:
            result.pop("_id", None)
            logger.info(f"Perfil archivado para empleado {empleado_id}")
            return PerfilDB(**result)
        logger.warning(f"Intento de archivar perfil inexistente: {empleado_id}")
        return None

    @staticmethod
    async def sincronizar_email(empleado_id: str, email: str) -> Optional[PerfilDB]:
        """Sincronizar email del perfil desde evento de actualizacion"""
        collection = get_collection()
        result = await collection.find_one_and_update(
            {"empleadoId": empleado_id},
            {"$set": {
                "email": email,
                "updatedAt": datetime.now(timezone.utc),
            }},
            return_document=True,
        )
        if result:
            result.pop("_id", None)
            logger.info(f"Email sincronizado para empleado {empleado_id}: {email}")
            return PerfilDB(**result)
        logger.warning(f"Intento de sincronizar email en perfil inexistente: {empleado_id}")
        return None

    @staticmethod
    async def sincronizar_datos(empleado_id: str, nombre: str, apellido: str) -> Optional[PerfilDB]:
        """Sincronizar datos del perfil desde evento de actualizacion"""
        collection = get_collection()
        result = await collection.find_one_and_update(
            {"empleadoId": empleado_id},
            {"$set": {
                "nombre": nombre,
                "apellido": apellido,
                "updatedAt": datetime.now(timezone.utc),
            }},
            return_document=True,
        )
        if result:
            result.pop("_id", None)
            logger.info(f"Perfil sincronizado para empleado {empleado_id}: nombre={nombre}, apellido={apellido}")
            return PerfilDB(**result)
        logger.warning(f"Intento de sincronizar perfil inexistente: {empleado_id}")
        return None
