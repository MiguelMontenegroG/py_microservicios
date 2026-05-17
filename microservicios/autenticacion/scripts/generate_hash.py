import bcrypt
# Forzar prefijo $2a$ (el default de bcrypt es $2b$ en Python)
salt = bcrypt.gensalt(rounds=12, prefix=b'2a')
hash = bcrypt.hashpw(b'Admin123!', salt)
print(hash.decode())
