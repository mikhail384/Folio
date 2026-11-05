"""VPN-friendly outbound HTTPS relay transport shared with Folio Android/Cloud."""
import asyncio
import base64
import hashlib
import hmac
import json
import random
import uuid
from urllib.parse import urlparse
from urllib.request import getproxies

import httpx
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from secrets import token_bytes


RETRYABLE_STATUS = {408, 425, 429, 500, 502, 503, 504}
RELAY_TIMEOUT = httpx.Timeout(10.0, connect=5.0, write=8.0, pool=3.0)
RELAY_PROXY_HOST = '109.66.236.217'
RELAY_PROXY_PORT = 17443
RELAY_PROXY_PATH = '/_folio-cloud'


class CloudflareBlocked(Exception):
    pass


class TemporaryRelayFailure(Exception):
    pass


def encode(data):
    return base64.urlsafe_b64encode(data).decode().rstrip('=')


def decode(value):
    return base64.urlsafe_b64decode(value + '=' * ((-len(value)) % 4))


def role_token(token, role, room):
    return encode(hmac.new(decode(token), ('folio-' + role + ':' + room).encode(), hashlib.sha256).digest())


def seal(token, value, aad):
    iv = token_bytes(12)
    data = AESGCM(decode(token)).encrypt(
        iv, json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode(), aad.encode()
    )
    return {'iv': encode(iv), 'data': encode(data)}


def open_envelope(token, value, aad):
    return json.loads(AESGCM(decode(token)).decrypt(decode(value['iv']), decode(value['data']), aad.encode()))


def system_proxy(url):
    target = urlparse(url)
    if target.hostname in ('localhost', '127.0.0.1', '::1'):
        return None
    proxies = getproxies()
    return proxies.get(target.scheme) or proxies.get('all')


def _is_cloudflare_block(response):
    if response.status_code != 403 or 'text/html' not in response.headers.get('content-type', '').lower():
        return False
    return 'cf-ray' in response.headers or 'cloudflare' in response.headers.get('server', '').lower()


def _retry_delay(response, attempt):
    retry_after = response.headers.get('retry-after', '')
    try:
        return min(2.0, max(0.15, float(retry_after)))
    except (TypeError, ValueError):
        return min(1.5, 0.2 * (2 ** attempt)) + random.uniform(0.0, 0.12)


def _http_error(response):
    if _is_cloudflare_block(response):
        raise CloudflareBlocked()
    try:
        data = response.json()
        message = data.get('error', 'Ошибка облака Folio') if isinstance(data, dict) else 'Ошибка облака Folio'
    except ValueError:
        message = 'Облачный сервер Folio вернул непонятный ответ'
    raise ValueError(f'Folio Cloud {response.status_code}: {message}')


async def _post_with_retries(client, target, operation, body, headers):
    endpoint = target
    last_error = None
    for attempt in range(2):
        try:
            response = await client.post(
                endpoint.rstrip('/') + '/relay/v1/' + operation,
                json=body,
                headers=headers,
            )
        except httpx.RequestError as error:
            last_error = error
            if attempt < 1:
                await asyncio.sleep(min(1.2, 0.2 * (2 ** attempt)) + random.uniform(0.0, 0.12))
                continue
            raise TemporaryRelayFailure() from error

        if _is_cloudflare_block(response):
            raise CloudflareBlocked()
        if response.status_code in RETRYABLE_STATUS and attempt < 1:
            await asyncio.sleep(_retry_delay(response, attempt))
            continue
        if response.status_code in RETRYABLE_STATUS and response.status_code != 429:
            raise TemporaryRelayFailure()
        if response.status_code != 200:
            _http_error(response)
        try:
            result = response.json()
        except ValueError as error:
            raise TemporaryRelayFailure() from error
        if not isinstance(result, dict):
            raise TemporaryRelayFailure()
        return result
    raise TemporaryRelayFailure() from last_error


async def call(config, route, payload):
    url = config['url'].rstrip('/')
    room = config['room']
    token = config['token']
    request_id = uuid.uuid4().hex
    headers = {
        'Authorization': 'Bearer ' + role_token(token, 'client', room),
        'OAI-Sites-Authorization': 'Bearer ' + config['service_token'],
        'Cache-Control': 'no-store',
    }

    proxy_url = config.get('relay_proxy_url')
    if not proxy_url and urlparse(url).hostname == 'folio-canvas-link.mikhailllm.chatgpt.site':
        proxy_url = f'https://{RELAY_PROXY_HOST}:{RELAY_PROXY_PORT}{RELAY_PROXY_PATH}'
    targets = [url]
    if proxy_url and proxy_url.rstrip('/') != url:
        targets.append(proxy_url.rstrip('/'))
    active = 0

    async with httpx.AsyncClient(timeout=RELAY_TIMEOUT, trust_env=True, proxy=system_proxy(url), follow_redirects=False) as client:
        async def post(operation, body):
            nonlocal active
            try:
                return await _post_with_retries(client, targets[active], operation, {'room': room, **body}, headers)
            except (CloudflareBlocked, TemporaryRelayFailure, httpx.RequestError) as error:
                if active == 0 and len(targets) > 1:
                    active = 1
                    try:
                        return await _post_with_retries(client, targets[active], operation, {'room': room, **body}, headers)
                    except (CloudflareBlocked, TemporaryRelayFailure, httpx.RequestError) as fallback_error:
                        if isinstance(fallback_error, CloudflareBlocked):
                            raise ValueError(
                                'Cloudflare отклонил и основной маршрут, и HTTPS-шлюз Folio. '
                                'Ключ подключения не меняйте; перед повторной записью прочитайте холст.'
                            ) from fallback_error
                        raise ValueError(
                            'Folio Cloud недоступен по основному и резервному HTTPS-маршрутам. '
                            'VPN можно оставить включённым; повторную запись сначала проверьте чтением холста.'
                        ) from fallback_error
                if isinstance(error, CloudflareBlocked):
                    raise ValueError(
                        'Cloudflare блокирует VPN-маршрут к Folio Cloud (403). '
                        'Это не ошибка ключа подключения; копировать его заново не нужно.'
                    ) from error
                if isinstance(error, TemporaryRelayFailure) or isinstance(error, httpx.RequestError):
                    raise ValueError('Folio Cloud временно недоступен по HTTPS. VPN оставьте включённым и повторите запрос.') from error
                raise

        await post('request', {
            'id': request_id,
            'envelope': seal(token, {'route': route, 'payload': payload}, 'folio:' + request_id + ':request'),
        })
        until = asyncio.get_running_loop().time() + 35
        while asyncio.get_running_loop().time() < until:
            result = await post('result', {'id': request_id})
            if result.get('envelope'):
                value = open_envelope(token, result['envelope'], 'folio:' + request_id + ':response')
                if not value.get('ok'):
                    raise ValueError('Folio: ' + value.get('error', 'Холст отклонил команду'))
                return value['result']
            await asyncio.sleep(0.5)
        raise ValueError(
            'Планшет не ответил за 35 секунд. Оставьте Folio с открытой тетрадью и включённым облачным доступом. '
            'Перед повторной записью прочитайте холст.'
        )
