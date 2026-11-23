#!/usr/bin/env python3
"""Proxy HTTP minimo para probar la app del emulador contra el servidor local del Mac.

El emulador no puede llegar a https://bfcastellet.localhost (para Android, *.localhost es
el propio emulador, y sin root no se puede redirigir el puerto 443). Con este proxy el
WebView de la app (solo en debug con -PurlLocal) sale por el Mac, y *.localhost se resuelve
aqui a 127.0.0.1, asi que llega al Apache local con el mismo host y puerto que en el navegador.

Emulador o movil por USB:
    python3 .github/scripts/proxy_local.py            # escucha en 127.0.0.1:8888
    adb reverse tcp:8888 tcp:8888
    ./gradlew installBasicfactoryDebug -PurlLocal=https://bfcastellet.localhost

Movil por wifi (APK instalada a mano; mismo wifi que el Mac):
    python3 .github/scripts/proxy_local.py 8888 192.168.0.106   # tambien en la IP del Mac
    ./gradlew assembleBasicfactoryDebug -PurlLocal=https://bfcastellet.localhost -PproxyLocal=192.168.0.106:8888

Soporta CONNECT (https) y peticiones http con URL absoluta. Por defecto solo escucha en
127.0.0.1; con una IP de la red local, cualquiera de esa red puede usarlo mientras este en
marcha, asi que paralo al terminar.
"""
import asyncio
import sys
from urllib.parse import urlsplit

PUERTO = int(sys.argv[1]) if len(sys.argv) > 1 else 8888
HOSTS = ['127.0.0.1'] + sys.argv[2:]


def destino(host):
    return '127.0.0.1' if host == 'localhost' or host.endswith('.localhost') else host


async def tuberia(lector, escritor):
    try:
        while data := await lector.read(65536):
            escritor.write(data)
            await escritor.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        escritor.close()


async def atender(cliente_r, cliente_w):
    try:
        cabecera = await cliente_r.readuntil(b'\r\n\r\n')
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, ConnectionError):
        cliente_w.close()
        return
    linea, _, resto = cabecera.partition(b'\r\n')
    metodo, objetivo, version = linea.decode('latin-1').split(' ', 2)

    try:
        if metodo == 'CONNECT':
            host, _, puerto = objetivo.rpartition(':')
            srv_r, srv_w = await asyncio.open_connection(destino(host), int(puerto))
            cliente_w.write(b'HTTP/1.1 200 Connection established\r\n\r\n')
            await cliente_w.drain()
        else:
            u = urlsplit(objetivo)
            srv_r, srv_w = await asyncio.open_connection(destino(u.hostname), u.port or 80)
            ruta = (u.path or '/') + (('?' + u.query) if u.query else '')
            srv_w.write(f'{metodo} {ruta} {version}\r\n'.encode('latin-1') + resto)
            await srv_w.drain()
        print(f'{metodo} {objetivo}', flush=True)
    except OSError as e:
        print(f'ERROR {metodo} {objetivo}: {e}', flush=True)
        cliente_w.write(b'HTTP/1.1 502 Bad Gateway\r\n\r\n')
        cliente_w.close()
        return

    await asyncio.gather(tuberia(cliente_r, srv_w), tuberia(srv_r, cliente_w))


async def main():
    servidor = await asyncio.start_server(atender, HOSTS, PUERTO)
    print(f'Proxy local en {", ".join(HOSTS)} puerto {PUERTO}', flush=True)
    async with servidor:
        await servidor.serve_forever()


if __name__ == '__main__':
    asyncio.run(main())
