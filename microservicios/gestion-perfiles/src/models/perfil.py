from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel, Field


class Direccion(BaseModel):
    calle: Optional[str] = None
    ciudad: Optional[str] = None
    codigoPostal: Optional[str] = None
    pais: Optional[str] = None


class RedesSociales(BaseModel):
    linkedin: Optional[str] = None
    github: Optional[str] = None


class PerfilDB(BaseModel):
    """Modelo para almacenar en MongoDB"""
    empleadoId: str
    email: str = ""
    nombre: str = ""
    apellido: str = ""
    foto: Optional[str] = None
    biografia: Optional[str] = None
    telefono: Optional[str] = None
    direccion: Direccion = Field(default_factory=Direccion)
    redesSociales: RedesSociales = Field(default_factory=RedesSociales)
    archivado: bool = False
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updatedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

    def to_mongo_dict(self) -> dict:
        data = self.model_dump()
        return data

    def actualizar_timestamp(self):
        self.updatedAt = datetime.now(timezone.utc)

    @classmethod
    def desde_evento_creado(cls, empleado_id: str, email: str, nombre: str, apellido: str) -> "PerfilDB":
        return cls(
            empleadoId=empleado_id,
            email=email,
            nombre=nombre,
            apellido=apellido,
        )
