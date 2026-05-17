from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    MONGO_URL: str = "mongodb://perfiles_user:perfiles_pass@localhost:27017/perfiles_db?authSource=admin"
    RABBITMQ_URL: str = "amqp://guest:guest@localhost:5672"
    PORT: int = 8083
    SERVICE_NAME: str = "gestion-perfiles"

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "case_sensitive": True,
    }


@lru_cache()
def get_settings() -> Settings:
    return Settings()
