// Service Worker — кеширует приложение при первом открытии
// Следующие визиты работают даже если Cloudflare/сайт недоступен
const CACHE = 'schedule-app-v1';
const SHELL = ['/'];

// Устанавливаем: кешируем оболочку приложения
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

// Активируем: удаляем старые кеши
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Запросы: cache-first для оболочки, network-only для API/прокси
self.addEventListener('fetch', e => {
  const url = e.request.url;

  // API-запросы к Яндексу и прокси — всегда через сеть, не кешируем
  if (url.includes('yandex') || url.includes('allorigins') || url.includes('corsproxy')
      || url.includes('codetabs') || url.includes('thingproxy') || url.includes('googleapis')) {
    return;
  }

  // Оболочка приложения — сначала из кеша, при ошибке из сети
  e.respondWith(
    caches.match(e.request).then(cached => {
      if (cached) return cached;
      return fetch(e.request).then(response => {
        // Кешируем успешные ответы
        if (response && response.status === 200 && response.type === 'basic') {
          const clone = response.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone));
        }
        return response;
      }).catch(() => caches.match('/'));
    })
  );
});
