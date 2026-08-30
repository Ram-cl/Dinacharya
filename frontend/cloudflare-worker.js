/**
 * Same-origin proxy so POST /api/v1/* hits Render instead of static assets (405).
 * Cloudflare Worker env: API_ORIGIN = https://YOUR-SERVICE.onrender.com
 *
 * Also runs a keep-alive cron every 10 minutes to prevent Render from spinning down.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) {
      const origin = String(env.API_ORIGIN || 'https://dinacharya-ese5.onrender.com').replace(
        /\/$/,
        ''
      );

      const target = origin + url.pathname + url.search;
      const headers = new Headers(request.headers);
      headers.delete('host');

      const body =
        request.method === 'GET' || request.method === 'HEAD'
          ? undefined
          : await request.arrayBuffer();

      const init = {
        method: request.method,
        headers,
        body,
        redirect: 'follow',
      };

      let response = await fetch(target, init);
      // Do not retry POST/PUT: a second import would re-run the whole file and
      // retry waits consume Cloudflare's 120s origin read timeout.
      const idempotent = request.method === 'GET' || request.method === 'HEAD';
      if (idempotent) {
        // Retry up to 3 times with 2s backoff (reduced from 4s × 4 to cut lag)
        for (let attempt = 0; attempt < 3; attempt++) {
          if (response.status !== 503 && response.status !== 429) {
            break;
          }
          await new Promise((resolve) => setTimeout(resolve, 2000));
          response = await fetch(target, init);
        }
      }
      return response;
    }

    return env.ASSETS.fetch(request);
  },

  // Cron trigger — runs every 10 minutes to keep Render awake
  async scheduled(_event, env, _ctx) {
    const origin = String(env.API_ORIGIN || 'https://dinacharya-ese5.onrender.com').replace(/\/$/, '');
    try {
      const res = await fetch(`${origin}/api/v1/actuator/health/liveness`, {
        method: 'GET',
        headers: { 'User-Agent': 'Cloudflare-KeepAlive/1.0' },
      });
      console.log(`[keep-alive] ${res.status} ${new Date().toISOString()}`);
    } catch (err) {
      console.error(`[keep-alive] failed: ${err}`);
    }
  },
};
