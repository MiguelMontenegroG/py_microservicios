from motor.motor_asyncio import AsyncIOMotorClient
from src.config import get_settings

settings = get_settings()

client: AsyncIOMotorClient | None = None
db = None


async def connect_to_mongo():
    global client, db
    client = AsyncIOMotorClient(settings.MONGO_URL)
    db = client.get_default_database()
    # Si no hay db name en la URL, usar el default
    if db.name is None:
        db = client.perfiles_db
    # Crear indice unico para empleadoId
    await db.perfiles.create_index("empleadoId", unique=True)
    return db


async def close_mongo_connection():
    global client
    if client:
        client.close()


def get_database():
    return db


def get_collection():
    if db is None:
        raise RuntimeError("Base de datos no inicializada. Llamar connect_to_mongo() primero.")
    return db.perfiles
