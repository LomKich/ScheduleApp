/**
 * Cloudflare Worker — CORS-прокси для Яндекс Диска API
 * 
 * Деплой:
 * 1. Зарегистрируйся на https://dash.cloudflare.com
 * 2. Workers & Pages → Create Worker
 * 3. Вставь этот код → Deploy
 * 4. Скопируй URL воркера (например: https://my-proxy.workers.dev)
 * 5. В приложении в настройках → Прокси: https://my-proxy.workers.dev/proxy/
 */

export default {
  async fetch(request, env, ctx) {
    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, HEAD, OPTIONS',
          'Access-Control-Allow-Headers': '*',
          'Access-Control-Max-Age': '86400',
        }
      });
    }

    const url = new URL(request.url);

    // Маршрут: /proxy/<encoded-url>
    if (!url.pathname.startsWith('/proxy/')) {
      return new Response('Proxy URL required. Use: /proxy/<encoded-url>', { status: 400 });
    }

    const targetEncoded = url.pathname.slice(7) + (url.search || '');
    let targetUrl;
    try {
      targetUrl = decodeURIComponent(targetEncoded);
    } catch (e) {
      return new Response('Invalid URL encoding', { status: 400 });
    }

    // Разрешаем только Яндекс и доверенные хосты
    const allowed = [
      'cloud-api.yandex.net',
      'downloader.disk.yandex.ru',
      'disk.yandex.ru',
      'downloader.disk.yandex.net',
    ];
    let parsedTarget;
    try {
      parsedTarget = new URL(targetUrl);
    } catch (e) {
      return new Response('Invalid target URL', { status: 400 });
    }
    if (!allowed.some(h => parsedTarget.hostname.endsWith(h))) {
      return new Response(`Host not allowed: ${parsedTarget.hostname}`, { status: 403 });
    }

    // Проксируем запрос
    try {
      const proxyReq = new Request(targetUrl, {
        method: request.method,
        headers: {
          'User-Agent': 'Mozilla/5.0 ScheduleApp/1.0',
          'Accept': request.headers.get('Accept') || '*/*',
          'Accept-Language': 'ru',
        },
        body: ['GET', 'HEAD'].includes(request.method) ? null : request.body,
        redirect: 'follow',
      });

      const resp = await fetch(proxyReq);
      const body = await resp.arrayBuffer();

      return new Response(body, {
        status: resp.status,
        headers: {
          'Content-Type': resp.headers.get('Content-Type') || 'application/octet-stream',
          'Content-Length': body.byteLength,
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': '*',
          'Cache-Control': 'no-cache',
        }
      });
    } catch (e) {
      return new Response('Proxy error: ' + e.message, { status: 502 });
    }
  }
};
